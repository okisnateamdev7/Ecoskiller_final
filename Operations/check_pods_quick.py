import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== CHECKING K3S SERVICE ==='",
    "systemctl status k3s --no-pager || true",
    "echo '=== CHECKING PODS ==='",
    "kubectl get pods -A --request-timeout=10s || true"
]

try:
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={'commands': commands},
        TimeoutSeconds=60
    )
    command_id = response['Command']['CommandId']
    
    for i in range(20):
        time.sleep(2)
        output = ssm.list_command_invocations(
            CommandId=command_id,
            InstanceId=instance_id,
            Details=True
        )
        if not output['CommandInvocations']:
            continue
        inv = output['CommandInvocations'][0]
        status = inv['Status']
        if status in ['Success', 'Failed', 'Cancelled', 'TimedOut']:
            print(f"Final Status: {status}")
            print(inv['CommandPlugins'][0]['Output'])
            if 'StandardErrorContent' in inv['CommandPlugins'][0]:
                print(inv['CommandPlugins'][0]['StandardErrorContent'])
            sys.exit(0)
    print("Timeout checking pods")
except Exception as e:
    print(f"Error: {e}")
