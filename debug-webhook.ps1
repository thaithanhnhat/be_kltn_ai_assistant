# Facebook Webhook Debug Script
Write-Host "=== Facebook Webhook Debug ===" -ForegroundColor Green

# Check if application is running
Write-Host "`nChecking if application is running..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/assistant/api/test/health" -Method GET
    Write-Host "✅ Application is running!" -ForegroundColor Green
    Write-Host "Response: $($response | ConvertTo-Json)" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Application is NOT running on port 8080" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "`nPlease start the application with: mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# Test webhook test endpoint
Write-Host "`nTesting webhook test endpoint..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/assistant/api/test/webhook-test" -Method GET
    Write-Host "✅ Webhook test endpoint accessible!" -ForegroundColor Green
    Write-Host "Response: $response" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Webhook test endpoint failed" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Check ngrok status
Write-Host "`nChecking ngrok status..." -ForegroundColor Yellow
try {
    $ngrokResponse = Invoke-RestMethod -Uri "http://localhost:4040/api/tunnels" -Method GET
    $tunnel = $ngrokResponse.tunnels | Where-Object { $_.proto -eq "https" }
    
    if ($tunnel) {
        $ngrokUrl = $tunnel.public_url
        Write-Host "✅ Ngrok is running!" -ForegroundColor Green
        Write-Host "Public URL: $ngrokUrl" -ForegroundColor Cyan
          # Test webhook through ngrok
        Write-Host "`nTesting webhook through ngrok..." -ForegroundColor Yellow
        try {
            $testUrl = "$ngrokUrl/assistant/api/test/webhook-test"
            $response = Invoke-RestMethod -Uri $testUrl -Method GET
            Write-Host "✅ Ngrok webhook test successful!" -ForegroundColor Green
            Write-Host "Response: $response" -ForegroundColor Cyan
        } catch {
            Write-Host "❌ Ngrok webhook test failed" -ForegroundColor Red
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
        
        # Test Facebook webhook verification endpoint
        Write-Host "`nTesting Facebook webhook verification..." -ForegroundColor Yellow
        try {
            $webhookUrl = "$ngrokUrl/assistant/api/facebook/webhook/7"
            $testParams = @{
                'hub.mode' = 'subscribe'
                'hub.verify_token' = 'test-token'
                'hub.challenge' = 'test-challenge'
            }
            
            $queryString = ($testParams.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
            $fullUrl = "$webhookUrl?$queryString"
            
            Write-Host "Testing URL: $fullUrl" -ForegroundColor Cyan
            
            $response = Invoke-RestMethod -Uri $fullUrl -Method GET
            Write-Host "✅ Facebook webhook verification accessible!" -ForegroundColor Green
            Write-Host "Response: $response" -ForegroundColor Cyan
        } catch {
            Write-Host "❌ Facebook webhook verification failed" -ForegroundColor Red
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            
            # Check if it's a 400 Bad Request (expected for invalid token)
            if ($_.Exception.Response.StatusCode -eq 400) {
                Write-Host "ℹ️  This is expected - the test token is invalid" -ForegroundColor Blue
                Write-Host "   The endpoint is accessible, just need valid verify token" -ForegroundColor Blue
            }
        }
        
    } else {
        Write-Host "❌ Ngrok is not running or no HTTPS tunnel found" -ForegroundColor Red
        Write-Host "Please start ngrok with: ngrok http 8080" -ForegroundColor Yellow
    }
} catch {
    Write-Host "❌ Cannot connect to ngrok API (http://localhost:4040)" -ForegroundColor Red
    Write-Host "Please start ngrok with: ngrok http 8080" -ForegroundColor Yellow
}

Write-Host "`n=== Debug Complete ===" -ForegroundColor Green
