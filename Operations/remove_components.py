import subprocess
import json
import time
import sys
import os

# Reconfigure stdout to use UTF-8
sys.stdout.reconfigure(encoding='utf-8')

# Ensure environment forces UTF-8 for subprocesses
env = os.environ.copy()
env['PYTHONIOENCODING'] = 'utf-8'
env['AWS_CLI_FILE_ENCODING'] = 'utf-8'

instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== STOPPING & REMOVING K3S-TEST ==='",
    "sudo systemctl stop k3s-test || true",
    "sudo systemctl disable k3s-test || true",
    "sudo rm -f /etc/systemd/system/k3s-test.service",
    "sudo systemctl daemon-reload",
    "sudo rm -rf /var/lib/rancher/k3s-test",
    "sudo rm -f /etc/rancher/k3s/k3s-test.yaml",
    "echo 'K3s-test removed successfully.'",
    
    "echo '=== CLEANING DOCKER ==='",
    "if command -v docker &> /dev/null; then",
    "  docker stop $(docker ps -aq) || true",
    "  docker rm $(docker ps -aq) || true",
    "  docker system prune -af --volumes || true",
    "else",
    "  echo 'Docker not installed or not running.'",
    "fi",
    
    "echo '=== REMOVING K8S NAMESPACES ==='",
    "echo 'Deleting cert-manager namespace...'",
    "kubectl delete namespace cert-manager --timeout=45s || true",
    "echo 'Deleting shared-infra namespace...'",
    "kubectl delete namespace shared-infra --timeout=45s || true",
    
    "echo '=== POST-CLEANUP K8S STATUS ==='",
    "kubectl get ns",
    "kubectl get pods -A"
]

print("Sending cleanup command via SSM...")
cmd_args = [
    "aws", "ssm", "send-command",
    "--instance-ids", instance_id,
    "--document-name", "AWS-RunShellScript",
    "--parameters", f"commands={json.dumps(commands)}",
    "--output", "json"
]

try:
    res_bytes = subprocess.check_output(cmd_args, env=env)
    res = json.loads(res_bytes.decode('utf-8', errors='replace'))
    cmd_id = res['Command']['CommandId']
    print(f"Command sent successfully. Command ID: {cmd_id}")
except Exception as e:
    print(f"Failed to send SSM command: {e}")
    sys.exit(1)

# Poll for execution
print("Waiting for cleanup to complete on the server...")
time.sleep(5)  # Wait for invocation to register

for attempt in range(1, 30):
    status_args = [
        "aws", "ssm", "get-command-invocation",
        "--command-id", cmd_id,
        "--instance-id", instance_id,
        "--output", "json"
    ]
    try:
        status_bytes = subprocess.check_output(status_args, env=env, stderr=subprocess.STDOUT)
        status_res = json.loads(status_bytes.decode('utf-8', errors='replace'))
    except subprocess.CalledProcessError as e:
        err_msg = e.output.decode('utf-8', errors='replace')
        if "InvocationDoesNotExist" in err_msg:
            print(f"[{attempt}] Invocation does not exist yet. Retrying...")
        else:
            clean_err = err_msg.encode('ascii', errors='replace').decode('ascii')
            print(f"[{attempt}] Error checking status: {clean_err.strip()}")
        time.sleep(4)
        continue
    except Exception as e:
        print(f"[{attempt}] Unexpected error: {e}")
        time.sleep(4)
        continue

    status = status_res.get('Status')
    print(f"[{attempt}] Status: {status}")
    if status in ['Success', 'Failed', 'Cancelled', 'TimedOut']:
        print("\n================= STDOUT =================")
        stdout_content = status_res.get('StandardOutputContent', '')
        # Replace non-ascii arrows and bullets safely for printing
        clean_stdout = stdout_content.replace('→', '->').replace('●', '*')
        print(clean_stdout.strip())
        
        print("\n================= STDERR =================")
        stderr_content = status_res.get('StandardErrorContent', '')
        clean_stderr = stderr_content.replace('→', '->').replace('●', '*')
        print(clean_stderr.strip())
        break
    time.sleep(5)
