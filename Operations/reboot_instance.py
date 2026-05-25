import boto3

ssm = boto3.client('ssm', region_name='ap-south-1')

instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== REBOOTING INSTANCE ==='",
    "sudo reboot"
]

print("Sending SSM command to reboot...")
response = ssm.send_command(
    InstanceIds=[instance_id],
    DocumentName="AWS-RunShellScript",
    Parameters={'commands': commands}
)

command_id = response['Command']['CommandId']
print(f"Command sent successfully. Command ID: {command_id}. The instance will reboot now.")
