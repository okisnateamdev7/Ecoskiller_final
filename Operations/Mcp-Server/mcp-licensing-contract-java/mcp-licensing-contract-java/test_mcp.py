import subprocess
import json
import base64
import sys
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')


payload = {
    "sub": "test-actor-uuid",
    "iss": "http://keycloak.ecoskiller.internal/realms/ecoskiller",
    "exp": 9999999999,
    "realm_access": {
        "roles": [
            "ecoskiller:licensing:create",
            "ecoskiller:licensing:admin",
            "ecoskiller:viewer"
        ]
    }
}
payload_encoded = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
test_jwt = f"eyJhbGciOiJSUzI1NiJ9.{payload_encoded}.fakesig"

jar = "target/licensing-contract-mcp-1.0.0.jar"

def run_test(name, req_obj):
    print(f"--- {name} ---")
    proc = subprocess.Popen(["java", "-jar", jar], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='replace')
    req_str = json.dumps(req_obj) + "\n"
    stdout, stderr = proc.communicate(req_str)
    print("STDOUT:")
    print(stdout.strip())
    if stderr.strip():
        print("STDERR:")
        print(stderr.strip())
    print()

run_test("initialize", {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {}
    }
})

run_test("tools/list", {
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
})

run_test("contract_lifecycle.create", {
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
        "name": "contract_lifecycle",
        "arguments": {
            "action": "create",
            "jwt_token": test_jwt,
            "idea_id": "550e8400-e29b-41d4-a716-446655440000",
            "licensee_business_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
            "idea_owner_candidate_id": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
            "tenant_id": "acme-corp",
            "proposed_royalty_rate": 0.0003,
            "proposed_term_years": 12,
            "territorial_scope": "national",
            "usage_scope": "non-exclusive"
        }
    }
})

run_test("royalty_rate_validation", {
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
        "name": "royalty_rate_validation",
        "arguments": {
            "action": "validate",
            "jwt_token": test_jwt,
            "proposed_rate": 0.0003,
            "proposed_term_years": 12
        }
    }
})
