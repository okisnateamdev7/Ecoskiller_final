import boto3

ec2 = boto3.client('ec2', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

try:
    print("Force stopping instance...")
    ec2.stop_instances(InstanceIds=[instance_id], Force=True)
    print("Force stop command sent.")
except Exception as e:
    print(f"Error: {e}")
