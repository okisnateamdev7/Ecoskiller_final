import boto3

ec2 = boto3.client('ec2', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

try:
    response = ec2.describe_instances(InstanceIds=[instance_id])
    state = response['Reservations'][0]['Instances'][0]['State']['Name']
    print(f"Current State: {state}")
    
    status_res = ec2.describe_instance_status(InstanceIds=[instance_id])
    if status_res['InstanceStatuses']:
        sys_stat = status_res['InstanceStatuses'][0]['SystemStatus']['Status']
        inst_stat = status_res['InstanceStatuses'][0]['InstanceStatus']['Status']
        print(f"System Status: {sys_stat}")
        print(f"Instance Status: {inst_stat}")
    else:
        print("Status checks not available yet.")
except Exception as e:
    print(f"Error: {e}")
