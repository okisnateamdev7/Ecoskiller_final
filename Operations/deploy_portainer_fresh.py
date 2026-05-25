import boto3
import time
import sys

ssm = boto3.client('ssm', region_name='ap-south-1')
instance_id = "i-0dd57e2a880632966"

yaml_content = """
apiVersion: v1
kind: Namespace
metadata:
  name: portainer
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: portainer-sa-clusteradmin
  namespace: portainer
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: portainer
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: portainer-sa-clusteradmin
  namespace: portainer
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: portainer
  namespace: portainer
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: local-path
  resources:
    requests:
      storage: 10Gi
---
apiVersion: v1
kind: Service
metadata:
  name: portainer
  namespace: portainer
spec:
  ports:
    - name: http
      port: 9000
      targetPort: 9000
    - name: edge
      port: 8000
      targetPort: 8000
  selector:
    app.kubernetes.io/name: portainer
    app.kubernetes.io/instance: portainer
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: portainer
  namespace: portainer
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: portainer
      app.kubernetes.io/instance: portainer
  template:
    metadata:
      labels:
        app.kubernetes.io/name: portainer
        app.kubernetes.io/instance: portainer
    spec:
      serviceAccountName: portainer-sa-clusteradmin
      containers:
        - name: portainer
          image: "portainer/portainer-ce:2.19.5"
          imagePullPolicy: IfNotPresent
          args: ['--tunnel-port=30776']
          ports:
            - name: http
              containerPort: 9000
            - name: https
              containerPort: 9443
            - name: tcp-edge
              containerPort: 8000
          livenessProbe:
            httpGet:
              path: /
              port: 9443
              scheme: HTTPS
          readinessProbe:
            httpGet:
              path: /
              port: 9443
              scheme: HTTPS
          volumeMounts:
            - name: data
              mountPath: /data
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: portainer
"""

commands = [
    f"cat << 'EOF' > /tmp/portainer.yaml\n{yaml_content}\nEOF",
    "kubectl apply -f /tmp/portainer.yaml",
    "sleep 15",
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
