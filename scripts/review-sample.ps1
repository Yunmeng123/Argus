# Review the bundled sample diff. Start the app first (see README).
# Response is decoded as UTF-8 explicitly because PowerShell 5.1 defaults to
# ISO-8859-1 when the JSON response has no charset, which garbles Chinese text.
$ErrorActionPreference = "Stop"

$diffPath = Resolve-Path (Join-Path $PSScriptRoot "..\samples\sample.diff")
$bytes = [System.IO.File]::ReadAllBytes($diffPath)

$resp = Invoke-WebRequest -Uri "http://localhost:18080/api/review/diff" `
    -Method Post `
    -ContentType "text/plain; charset=utf-8" `
    -Body $bytes `
    -UseBasicParsing

$json = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
$result = $json | ConvertFrom-Json
$result | ConvertTo-Json -Depth 10
Write-Host ""
Write-Host "Markdown report: $($result.reportPath)"
