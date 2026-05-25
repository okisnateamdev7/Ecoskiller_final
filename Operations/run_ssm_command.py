import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== CHECK K3S & PORTAINER ==='",
    "kubectl get pods -A",
    "echo '=== INSTALL PORTAINER ==='",
    "kubectl create namespace portainer || true",
    "kubectl apply -n portainer -f https://raw.githubusercontent.com/portainer/k8s/master/deploy/manifests/portainer/portainer.yaml",
    "cat << 'EOF' > /tmp/portainer-ingress.yaml\napiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: portainer-ingress\n  namespace: portainer\n  annotations:\n    kubernetes.io/ingress.class: \"traefik\"\nspec:\n  rules:\n  - host: dashboard.okisna.com\n    http:\n      paths:\n      - path: /\n        pathType: Prefix\n        backend:\n          service:\n            name: portainer\n            port:\n              number: 9000\nEOF\n",
    "kubectl apply -f /tmp/portainer-ingress.yaml",
    "echo '=== WAIT 10s ==='",
    "sleep 10",
    "echo '=== FINAL POD STATUS ==='",
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
    print(f"Command ID: {command_id}")

    for i in range(40):
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
            print(f"Final Status: {status}")
            print(inv['CommandPlugins'][0]['Output'])
            if 'StandardErrorContent' in inv['CommandPlugins'][0]:
                print(inv['CommandPlugins'][0]['StandardErrorContent'])
            sys.exit(0)
    print("Timeout waiting for SSM command")
except Exception as e:
    print(f"Error: {e}")
