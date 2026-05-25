import boto3

ec2 = boto3.client('ec2', region_name='ap-south-1')
ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

try:
    response = ec2.describe_instances(InstanceIds=[instance_id])
    state = response['Reservations'][0]['Instances'][0]['State']['Name']
    print(f"EC2 State: {state}")
except Exception as e:
    print(f"EC2 Error: {e}")

try:
    ssm_info = ssm.describe_instance_information(InstanceInformationFilterList=[{'key': 'InstanceIds', 'valueSet': [instance_id]}])
    info_list = ssm_info.get('InstanceInformationList', [])
    if info_list:
        ping_status = info_list[0]['PingStatus']
        print(f"SSM Ping Status: {ping_status}")
    else:
        print("SSM Agent: Not checking in.")
except Exception as e:
    print(f"SSM Error: {e}")
