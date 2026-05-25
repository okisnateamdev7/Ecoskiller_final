import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== UNINSTALLING K3S ==='",
    "/usr/local/bin/k3s-uninstall.sh || true",
    "echo '=== CLEANING UP REMAINS ==='",
    "rm -rf /var/lib/rancher",
    "rm -rf /etc/rancher",
    "rm -rf /var/lib/kubelet",
    "echo '=== INSTALLING K3S ==='",
    "curl -sfL https://get.k3s.io | sh -",
    "sleep 30",
    "echo '=== K3S STATUS ==='",
    "systemctl status k3s --no-pager || true",
    "kubectl get pods -A"
]

try:
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={'commands': commands},
        TimeoutSeconds=120
    )
    command_id = response['Command']['CommandId']
    
    for i in range(40):
        time.sleep(3)
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
            plugin_out = inv['CommandPlugins'][0]['Output']
            print(plugin_out.encode('cp1252', 'replace').decode('cp1252'))
            if 'StandardErrorContent' in inv['CommandPlugins'][0]:
                print(inv['CommandPlugins'][0]['StandardErrorContent'])
            sys.exit(0)
    print("Timeout")
except Exception as e:
    print(f"Error: {e}")
