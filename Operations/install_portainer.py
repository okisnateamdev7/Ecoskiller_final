import boto3
import time

ssm = boto3.client('ssm', region_name='ap-south-1')

instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== INSTALLING PORTAINER ==='",
    "kubectl create namespace portainer || true",
    "kubectl apply -n portainer -f https://raw.githubusercontent.com/portainer/k8s/master/deploy/manifests/portainer/portainer.yaml",
    "echo '=== CREATING INGRESS FOR PORTAINER ==='",
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
    "echo '=== WAITING FOR PODS ==='",
    "sleep 15",
    "echo '=== PODS STATUS ==='",
    "kubectl get pods -A"
]

print("Sending SSM command...")
try:
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={'commands': commands}
    )
    command_id = response['Command']['CommandId']
    print(f"Command sent successfully. Command ID: {command_id}")
    print("Waiting for command execution to complete...")

    while True:
        time.sleep(2)
        output = ssm.list_command_invocations(
            CommandId=command_id,
            InstanceId=instance_id,
            Details=True
        )
        if not output['CommandInvocations']:
            continue
        
        invocation = output['CommandInvocations'][0]
        status = invocation['Status']
        print(f"Status: {status}")
        
        if status in ['Success', 'Failed', 'Cancelled', 'TimedOut']:
            plugin_output = invocation['CommandPlugins'][0]['Output']
            print("\n================= STDOUT =================")
            print(plugin_output.encode('cp1252', 'replace').decode('cp1252'))
            
            # Check stderr if available
            if 'StandardErrorContent' in invocation['CommandPlugins'][0]:
                stderr_output = invocation['CommandPlugins'][0]['StandardErrorContent']
                if stderr_output:
                    print("\n================= STDERR =================")
                    print(stderr_output)
            break
except Exception as e:
    print(f"Error: {e}")
