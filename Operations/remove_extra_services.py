import boto3
import time

ssm = boto3.client('ssm', region_name='ap-south-1')

instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== REMOVING K3S-TEST.SERVICE ==='",
    "sudo systemctl stop k3s-test.service || true",
    "sudo systemctl disable k3s-test.service || true",
    "sudo rm -f /etc/systemd/system/k3s-test.service || true",
    "sudo systemctl daemon-reload || true",
    "echo '=== REMOVING PORTAINER ==='",
    "kubectl delete namespace portainer --force --grace-period=0 || true",
    "echo '=== REMOVING CERT-MANAGER & SHARED-INFRA ==='",
    "kubectl delete namespace cert-manager shared-infra --force --grace-period=0 || true",
    "echo '=== CHECKING REMAINING NAMESPACES ==='",
    "kubectl get ns",
    "echo '=== CHECKING REMAINING PODS ==='",
    "kubectl get pods -A"
]

print("Sending SSM command...")
response = ssm.send_command(
    InstanceIds=[instance_id],
    DocumentName="AWS-RunShellScript",
    Parameters={'commands': commands}
)

command_id = response['Command']['CommandId']
print(f"Command sent successfully. Command ID: {command_id}")
print("Waiting for command execution to complete...")

while True:
    time.sleep(2)
    output = ssm.list_command_invocations(
        CommandId=command_id,
        InstanceId=instance_id,
        Details=True
    )
    if not output['CommandInvocations']:
        continue
    
    invocation = output['CommandInvocations'][0]
    status = invocation['Status']
    print(f"Status: {status}")
    
    if status in ['Success', 'Failed', 'Cancelled', 'TimedOut']:
        plugin_output = invocation['CommandPlugins'][0]['Output']
        print("\n================= STDOUT =================")
        print(plugin_output)
        
        # Check stderr if available
        if 'StandardErrorContent' in invocation['CommandPlugins'][0]:
            stderr_output = invocation['CommandPlugins'][0]['StandardErrorContent']
            if stderr_output:
                print("\n================= STDERR =================")
                print(stderr_output)
        break
