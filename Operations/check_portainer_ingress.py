import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== PORTAINER INGRESS ==='",
    "kubectl get ingress -n portainer",
    "echo '=== CREATING INGRESS IF MISSING ==='",
    "cat << 'EOF' > /tmp/portainer-ingress.yaml\n"
    "apiVersion: networking.k8s.io/v1\n"
    "kind: Ingress\n"
    "metadata:\n"
    "  name: portainer-ingress\n"
    "  namespace: portainer\n"
    "  annotations:\n"
    "    kubernetes.io/ingress.class: \"traefik\"\n"
    "spec:\n"
    "  rules:\n"
    "  - host: dashboard.okisna.com\n"
    "    http:\n"
    "      paths:\n"
    "      - path: /\n"
    "        pathType: Prefix\n"
    "        backend:\n"
    "          service:\n"
    "            name: portainer\n"
    "            port:\n"
    "              number: 9000\n"
    "EOF\n",
    "kubectl apply -f /tmp/portainer-ingress.yaml",
    "echo '=== ALL PODS ==='",
    "kubectl get pods -n portainer"
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
