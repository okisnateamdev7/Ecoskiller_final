$payloadJson = '{"sub":"test-actor-uuid","iss":"http://keycloak.ecoskiller.internal/realms/ecoskiller","exp":9999999999,"realm_access":{"roles":["ecoskiller:licensing:create","ecoskiller:licensing:admin","ecoskiller:viewer"]}}'
$payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payloadJson)
$payloadBase64 = [Convert]::ToBase64String($payloadBytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
$testJwt = "eyJhbGciOiJSUzI1NiJ9.$payloadBase64.fakesig"

$jar = "target/licensing-contract-mcp-1.0.0.jar"

function Run-McpRequest($req) {
    Write-Host "--- Request ---"
    Write-Host $req
    $si = New-Object System.Diagnostics.ProcessStartInfo
    $si.FileName = "java"
    $si.Arguments = "-jar $jar"
    $si.RedirectStandardInput = $true
    $si.RedirectStandardOutput = $true
    $si.UseShellExecute = $false
    $p = [System.Diagnostics.Process]::Start($si)
    $p.StandardInput.WriteLine($req)
    $p.StandardInput.Close()
    $res = $p.StandardOutput.ReadToEnd()
    $p.WaitForExit()
    Write-Host "--- Response ---"
    Write-Host $res
    Write-Host ""
}

Run-McpRequest '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{}}}'
Run-McpRequest '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
Run-McpRequest "{\`"jsonrpc\`":\`"2.0\`",\`"id\`":3,\`"method\`":\`"tools/call\`",\`"params\`":{\`"name\`":\`"contract_lifecycle\`",\`"arguments\`":{\`"action\`":\`"create\`",\`"jwt_token\`":\`"$testJwt\`",\`"idea_id\`":\`"550e8400-e29b-41d4-a716-446655440000\`",\`"licensee_business_id\`":\`"6ba7b810-9dad-11d1-80b4-00c04fd430c8\`",\`"idea_owner_candidate_id\`":\`"6ba7b811-9dad-11d1-80b4-00c04fd430c8\`",\`"tenant_id\`":\`"acme-corp\`",\`"proposed_royalty_rate\`":0.0003,\`"proposed_term_years\`":12,\`"territorial_scope\`":\`"national\`",\`"usage_scope\`":\`"non-exclusive\`"}}}"
Run-McpRequest "{\`"jsonrpc\`":\`"2.0\`",\`"id\`":4,\`"method\`":\`"tools/call\`",\`"params\`":{\`"name\`":\`"royalty_rate_validation\`",\`"arguments\`":{\`"action\`":\`"validate\`",\`"jwt_token\`":\`"$testJwt\`",\`"proposed_rate\`":0.0003,\`"proposed_term_years\`":12}}}"
