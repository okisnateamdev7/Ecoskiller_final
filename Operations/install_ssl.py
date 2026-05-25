import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

cluster_issuer_yaml = """
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@okisna.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: traefik
"""

ingress_yaml = """
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: portainer-ingress
  namespace: portainer
  annotations:
    kubernetes.io/ingress.class: traefik
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - dashboard.okisna.com
    secretName: portainer-tls
  rules:
    - host: dashboard.okisna.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: portainer
                port:
                  number: 9000
"""

commands = [
    "echo '=== INSTALLING CERT-MANAGER ==='",
    "kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml",
    "echo '=== WAITING FOR CERT-MANAGER PODS ==='",
    "kubectl wait --for=condition=Ready pods -l app=cert-manager -n cert-manager --timeout=120s",
    "kubectl wait --for=condition=Ready pods -l app=webhook -n cert-manager --timeout=120s",
    "kubectl wait --for=condition=Ready pods -l app=cainjector -n cert-manager --timeout=120s",
    "sleep 10",
    "echo '=== APPLYING CLUSTER ISSUER ==='",
    f"cat << 'EOF' > /tmp/cluster-issuer.yaml\n{cluster_issuer_yaml}\nEOF",
    "kubectl apply -f /tmp/cluster-issuer.yaml",
    "echo '=== UPDATING INGRESS WITH TLS ==='",
    f"cat << 'EOF' > /tmp/portainer-ingress-tls.yaml\n{ingress_yaml}\nEOF",
    "kubectl apply -f /tmp/portainer-ingress-tls.yaml",
    "echo '=== CHECKING CERTIFICATE STATUS ==='",
    "sleep 15",
    "kubectl get certificate -n portainer"
]

try:
    response = ssm.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={'commands': commands},
        TimeoutSeconds=180
    )
    command_id = response['Command']['CommandId']
    
    for i in range(60):
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
