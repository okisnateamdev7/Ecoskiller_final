import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"
commands = [
    "echo '=== CLEARING KUBE-SYSTEM ERROR PODS ==='",
    "kubectl delete pods -n kube-system --field-selector status.phase=Failed",
    "kubectl delete pods -n kube-system -l k8s-app=metrics-server",
    "kubectl delete pods -n kube-system -l app.kubernetes.io/name=traefik",
    "echo '=== FIXING PORTAINER NAMESPACE FINALIZER ==='",
    "kubectl get namespace portainer -o json > /tmp/portainer.json",
    "sed -i 's/\"kubernetes\"//g' /tmp/portainer.json",
    "kubectl proxy &",
    "PROXY_PID=$!",
    "sleep 2",
    "curl -k -H \"Content-Type: application/json\" -X PUT --data-binary @/tmp/portainer.json http://127.0.0.1:8001/api/v1/namespaces/portainer/finalize",
    "kill $PROXY_PID || true",
    "sleep 3",
    "kubectl create namespace portainer || true",
    "echo '=== DEPLOYING PORTAINER VIA HELMCHART ==='",
    "cat << 'EOF' > /var/lib/rancher/k3s/server/manifests/portainer.yaml\n"
    "apiVersion: helm.cattle.io/v1\n"
    "kind: HelmChart\n"
    "metadata:\n"
    "  name: portainer\n"
    "  namespace: kube-system\n"
    "spec:\n"
    "  repo: https://portainer.github.io/k8s/\n"
    "  chart: portainer\n"
    "  targetNamespace: portainer\n"
    "  valuesContent: |-\n"
    "    tls:\n"
    "      force: false\n"
    "EOF\n",
    "echo '=== CREATING INGRESS ==='",
    "cat << 'EOF' > /var/lib/rancher/k3s/server/manifests/portainer-ingress.yaml\n"
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
    "sleep 15",
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
            print(f"Final Status: {status}")
            plugin_out = inv['CommandPlugins'][0]['Output']
            print(plugin_out.encode('cp1252', 'replace').decode('cp1252'))
            if 'StandardErrorContent' in inv['CommandPlugins'][0]:
                print(inv['CommandPlugins'][0]['StandardErrorContent'])
            sys.exit(0)
    print("Timeout checking pods")
except Exception as e:
    print(f"Error: {e}")
