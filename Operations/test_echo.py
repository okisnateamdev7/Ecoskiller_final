import boto3
import time

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = ["echo Hello"]

response = ssm.send_command(
    InstanceIds=[instance_id],
    DocumentName="AWS-RunShellScript",
    Parameters={'commands': commands}
)
command_id = response['Command']['CommandId']

while True:
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
        print(f"Status: {status}")
        print("Output:", inv['CommandPlugins'][0].get('Output', ''))
        print("Error:", inv['CommandPlugins'][0].get('StandardErrorContent', ''))
        break
