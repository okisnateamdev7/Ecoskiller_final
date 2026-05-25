import boto3
import subprocess
import os
import time

instance_id = "i-0dd57e2a880632966"
ec2 = boto3.client('ec2', region_name='ap-south-1')
eic = boto3.client('ec2-instance-connect', region_name='ap-south-1')

# 1. Get Public IP
res = ec2.describe_instances(InstanceIds=[instance_id])
public_ip = res['Reservations'][0]['Instances'][0].get('PublicIpAddress')
availability_zone = res['Reservations'][0]['Instances'][0]['Placement']['AvailabilityZone']

if not public_ip:
    print("No public IP found!")
    exit(1)

print(f"Public IP: {public_ip}, AZ: {availability_zone}")

# 2. Generate SSH key if not exists
key_path = "Operations\\id_rsa"
if not os.path.exists(key_path):
    subprocess.run(["ssh-keygen", "-t", "rsa", "-b", "2048", "-f", key_path, "-N", ""])

with open(f"{key_path}.pub", "r") as f:
    pub_key = f.read().strip()

# 3. Push SSH key via EC2 Instance Connect
print("Pushing SSH key to instance...")
res = eic.send_ssh_public_key(
    InstanceId=instance_id,
    InstanceOSUser='ubuntu',
    SSHPublicKey=pub_key,
    AvailabilityZone=availability_zone
)
if res['Success']:
    print("SSH key pushed successfully.")
else:
    print("Failed to push SSH key.")
    exit(1)

# 4. SSH and execute command
print("Executing SSH command...")
ssh_command = f'ssh -i {key_path} -o StrictHostKeyChecking=no ubuntu@{public_ip} "sudo apt-get update && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y apparmor && sudo systemctl enable apparmor && sudo systemctl start apparmor && sudo snap install amazon-ssm-agent --classic && sudo systemctl restart snapd && sudo systemctl restart snap.amazon-ssm-agent.amazon-ssm-agent.service"'

result = subprocess.run(ssh_command, shell=True, capture_output=True, text=True)
print("STDOUT:", result.stdout)
print("STDERR:", result.stderr)
print("Return Code:", result.returncode)
