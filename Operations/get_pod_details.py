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
    "echo '=== DESCRIBE TRAEFIK ==='",
    "kubectl describe pod -n kube-system -l app.kubernetes.io/name=traefik | tr -cd '\\11\\12\\15\\40-\\176' || true",
    "echo '=== LOGS TRAEFIK ==='",
    "kubectl logs -n kube-system -l app.kubernetes.io/name=traefik --tail=50 2>&1 | tr -cd '\\11\\12\\15\\40-\\176' || true",
    
    "echo '=== DESCRIBE PORTAINER ==='",
    "kubectl describe pod -n portainer -l app.kubernetes.io/name=portainer | tr -cd '\\11\\12\\15\\40-\\176' || true",
    "echo '=== LOGS PORTAINER ==='",
    "kubectl logs -n portainer -l app.kubernetes.io/name=portainer --tail=50 2>&1 | tr -cd '\\11\\12\\15\\40-\\176' || true",

    "echo '=== DESCRIBE SVCLB-TRAEFIK ==='",
    "kubectl describe pod -n kube-system -l svccontroller.k3s.cattle.io/svcname=traefik | tr -cd '\\11\\12\\15\\40-\\176' || true"
]

print("Sending SSM command...")
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
print("Waiting for command execution to complete...")
time.sleep(5)  # Wait for invocation to register

for attempt in range(1, 20):
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
        time.sleep(3)
        continue
    except Exception as e:
        print(f"[{attempt}] Unexpected error: {e}")
        time.sleep(3)
        continue

    status = status_res.get('Status')
    print(f"[{attempt}] Status: {status}")
    if status in ['Success', 'Failed', 'Cancelled', 'TimedOut']:
        print("\n================= STDOUT =================")
        stdout_content = status_res.get('StandardOutputContent', '')
        print(stdout_content.strip())
        
        print("\n================= STDERR =================")
        stderr_content = status_res.get('StandardErrorContent', '')
        print(stderr_content.strip())
        break
    time.sleep(4)
