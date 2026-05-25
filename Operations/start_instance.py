import boto3
import time

ec2 = boto3.client('ec2', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

try:
    print("Waiting for instance to fully stop...")
    waiter = ec2.get_waiter('instance_stopped')
    waiter.wait(InstanceIds=[instance_id])
    print("Instance is stopped.")
    
    print("Starting instance...")
    ec2.start_instances(InstanceIds=[instance_id])
    
    print("Waiting for instance to be running...")
    waiter = ec2.get_waiter('instance_running')
    waiter.wait(InstanceIds=[instance_id])
    print("Instance is running.")
    
    print("Waiting for status checks to pass...")
    waiter = ec2.get_waiter('instance_status_ok')
    waiter.wait(InstanceIds=[instance_id])
    print("Instance status checks passed.")
    
except Exception as e:
    print(f"Error: {e}")
