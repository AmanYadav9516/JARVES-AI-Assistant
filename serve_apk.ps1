$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:8080/")
try {
    $listener.Start()
    Write-Host "HTTP Server listening on http://192.168.1.2:8080/JARVES.apk"
} catch {
    $listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add("http://localhost:8080/")
    $listener.Start()
}

while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        Write-Host "Request received from $($request.RemoteEndPoint) for $($request.Url.LocalPath)"

        $filePath = "C:\JARVESS\JARVES.apk"
        if (Test-Path $filePath) {
            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            $response.ContentType = "application/vnd.android.package-archive"
            $response.ContentLength64 = $bytes.Length
            $response.AddHeader("Content-Disposition", "attachment; filename=JARVES.apk")
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $response.StatusCode = 404
        }
        $response.Close()
    } catch {
        Write-Host "Error processing request: $_"
    }
}
