import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = [
    "systemctl status k3s > /tmp/out.txt 2>&1 || true",
    "journalctl -u k3s -n 50 --no-pager >> /tmp/out.txt 2>&1 || true",
    "cat /tmp/out.txt"
]

try:
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={'commands': commands},
        TimeoutSeconds=60
    )
    command_id = response['Command']['CommandId']
    
    for i in range(15):
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
            sys.exit(0)
    print("Timeout")
except Exception as e:
    print(f"Error: {e}")
