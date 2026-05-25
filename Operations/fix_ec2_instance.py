import boto3
import time
import base64

ec2 = boto3.client('ec2', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

user_data = """Content-Type: multipart/mixed; boundary="//"
MIME-Version: 1.0

--//
Content-Type: text/cloud-boothook; charset="us-ascii"

#!/bin/bash
cat << 'EOF' > /etc/systemd/system/fix-apparmor.service
[Unit]
Description=Fix Apparmor
After=network-online.target

[Service]
Type=oneshot
ExecStart=/bin/bash -c "export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y apparmor && systemctl enable apparmor && systemctl start apparmor && systemctl restart snapd && snap install amazon-ssm-agent --classic && systemctl restart snap.amazon-ssm-agent.amazon-ssm-agent.service"

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable fix-apparmor.service
--//--"""

try:
    print("Stopping instance...")
    ec2.stop_instances(InstanceIds=[instance_id])
    
    waiter = ec2.get_waiter('instance_stopped')
    waiter.wait(InstanceIds=[instance_id])
    print("Instance stopped.")
    
    print("Modifying UserData...")
    ec2.modify_instance_attribute(
        InstanceId=instance_id,
        UserData={
            'Value': base64.b64encode(user_data.encode('utf-8')).decode('utf-8')
        }
    )
    
    print("Starting instance...")
    ec2.start_instances(InstanceIds=[instance_id])
    
    waiter = ec2.get_waiter('instance_running')
    waiter.wait(InstanceIds=[instance_id])
    print("Instance started and running.")
    
except Exception as e:
    print(f"Error: {e}")
