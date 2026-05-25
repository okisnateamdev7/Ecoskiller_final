import os
import http.server
import socketserver
import json

class ServiceHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        
        path = self.path
        if path in ["/recruitment/jobs", "/recruitment/candidates", "/recruitment/interviews", "/recruitment/onboarding"]:
            module = path.split('/')[-1]
            response = {"status": "success", "service": "recruitment-service", "module": module, "message": f"Endpoint {path} active"}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.wfile.write(b'{"status": "ok", "service": "recruitment-service", "message": "Root handler"}')

PORT = 8080
with socketserver.TCPServer(('', PORT), ServiceHandler) as httpd:
    print(f'recruitment-service starting on port {PORT}', flush=True)
    httpd.serve_forever()
