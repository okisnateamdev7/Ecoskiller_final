import boto3
import time

ssm = boto3.client('ssm', region_name='ap-south-1')

instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== CHECKING PORTAINER ==='",
    "kubectl get namespace portainer || true",
    "kubectl get pods -n portainer || true",
    "echo '=== INSTALLING PORTAINER (if missing) ==='",
    "kubectl get namespace portainer || kubectl create namespace portainer",
    "kubectl apply -n portainer -f https://raw.githubusercontent.com/portainer/k8s/master/deploy/manifests/portainer/portainer.yaml",
    "sleep 10",
    "echo '=== PORTAINER PODS STATUS ==='",
    "kubectl get pods -n portainer"
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
