import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
config_tmpl = """
[plugins."io.containerd.grpc.v1.cri"]
  disable_apparmor = true
"""

commands = [
    "echo '=== CONFIGURING CONTAINERD ==='",
    f"echo '{config_tmpl}' > /var/lib/rancher/k3s/agent/etc/containerd/config.toml.tmpl",
    "echo '=== RESTARTING K3S ==='",
    "systemctl restart k3s",
    "sleep 15",
    "echo '=== DELETING ALL PODS ==='",
    "kubectl delete pods --all -n kube-system",
    "kubectl delete pods --all -n portainer",
    "sleep 10",
    "echo '=== PODS STATUS ==='",
    "kubectl get pods -A"
]

try:
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={'commands': commands},
        TimeoutSeconds=60
    )
    command_id = response['Command']['CommandId']
    
    for i in range(25):
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
            plugin_out = inv['CommandPlugins'][0]['Output']
            print(plugin_out.encode('cp1252', 'replace').decode('cp1252'))
            if 'StandardErrorContent' in inv['CommandPlugins'][0]:
                print(inv['CommandPlugins'][0]['StandardErrorContent'])
            sys.exit(0)
    print("Timeout")
except Exception as e:
    print(f"Error: {e}")
