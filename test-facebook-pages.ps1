# Facebook Multiple Pages Integration Test Script
# This script tests the Facebook multiple pages functionality

param(
    [string]$BaseUrl = "http://localhost:8080/assistant",
    [string]$ShopId = "1",
    [string]$PageId = "TEST_PAGE_ID_123",
    [string]$AccessToken = "TEST_ACCESS_TOKEN",
    [string]$VerifyToken = "test_verify_token_123"
)

Write-Host "🚀 Facebook Multiple Pages Integration Test" -ForegroundColor Green
Write-Host "Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "Shop ID: $ShopId" -ForegroundColor Cyan
Write-Host "Page ID: $PageId" -ForegroundColor Cyan

# Function to make HTTP requests
function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [hashtable]$Headers = @{'Content-Type' = 'application/json'}
    )
    
    try {
        if ($Body) {
            $JsonBody = $Body | ConvertTo-Json -Depth 10
            $Response = Invoke-RestMethod -Uri $Url -Method $Method -Body $JsonBody -Headers $Headers
        } else {
            $Response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers
        }
        return @{Success = $true; Data = $Response}
    } catch {
        return @{Success = $false; Error = $_.Exception.Message; Response = $_.Exception.Response}
    }
}

Write-Host "`n📋 Test 1: Add Facebook Page to Shop" -ForegroundColor Yellow

$AddPageUrl = "$BaseUrl/api/facebook-pages/shops/$ShopId/pages"
$AddPageBody = @{
    pageId = $PageId
    pageName = "Test Shop Page"
    accessToken = $AccessToken
    verifyToken = $VerifyToken
    webhookUrl = "$BaseUrl/api/facebook/webhook"
    subscribedEvents = @("messages", "messaging_postbacks", "message_deliveries")
}

$AddResult = Invoke-ApiRequest -Method "POST" -Url $AddPageUrl -Body $AddPageBody

if ($AddResult.Success) {
    Write-Host "✅ Successfully added Facebook page" -ForegroundColor Green
    Write-Host ($AddResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to add Facebook page: $($AddResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 2: Get Shop Pages" -ForegroundColor Yellow

$GetPagesUrl = "$BaseUrl/api/facebook-pages/shops/$ShopId/pages"
$GetPagesResult = Invoke-ApiRequest -Method "GET" -Url $GetPagesUrl

if ($GetPagesResult.Success) {
    Write-Host "✅ Successfully retrieved shop pages" -ForegroundColor Green
    Write-Host ($GetPagesResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to get shop pages: $($GetPagesResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 3: Get Specific Page Configuration" -ForegroundColor Yellow

$GetPageUrl = "$BaseUrl/api/facebook-pages/shops/$ShopId/pages/$PageId"
$GetPageResult = Invoke-ApiRequest -Method "GET" -Url $GetPageUrl

if ($GetPageResult.Success) {
    Write-Host "✅ Successfully retrieved page configuration" -ForegroundColor Green
    Write-Host ($GetPageResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to get page configuration: $($GetPageResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 4: Update Page Configuration" -ForegroundColor Yellow

$UpdatePageUrl = "$BaseUrl/api/facebook-pages/shops/$ShopId/pages/$PageId"
$UpdatePageBody = @{
    pageName = "Updated Test Shop Page"
    subscribedEvents = @("messages", "messaging_postbacks")
}

$UpdateResult = Invoke-ApiRequest -Method "PUT" -Url $UpdatePageUrl -Body $UpdatePageBody

if ($UpdateResult.Success) {
    Write-Host "✅ Successfully updated page configuration" -ForegroundColor Green
    Write-Host ($UpdateResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to update page configuration: $($UpdateResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 5: Test Facebook Webhook Verification" -ForegroundColor Yellow

$WebhookVerifyUrl = "$BaseUrl/api/facebook/webhook?hub.verify_token=$VerifyToken&hub.challenge=test_challenge_123&hub.mode=subscribe"
$WebhookResult = Invoke-ApiRequest -Method "GET" -Url $WebhookVerifyUrl

if ($WebhookResult.Success) {
    Write-Host "✅ Webhook verification successful" -ForegroundColor Green
    Write-Host "Challenge response: $($WebhookResult.Data)" -ForegroundColor Gray
} else {
    Write-Host "❌ Webhook verification failed: $($WebhookResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 6: Test Message Analytics" -ForegroundColor Yellow

$AnalyticsUrl = "$BaseUrl/api/facebook-analytics/shops/$ShopId/message-stats"
$AnalyticsResult = Invoke-ApiRequest -Method "GET" -Url $AnalyticsUrl

if ($AnalyticsResult.Success) {
    Write-Host "✅ Successfully retrieved message analytics" -ForegroundColor Green
    Write-Host ($AnalyticsResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to get message analytics: $($AnalyticsResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 7: Test Dashboard Data" -ForegroundColor Yellow

$DashboardUrl = "$BaseUrl/api/facebook-analytics/shops/$ShopId/dashboard"
$DashboardResult = Invoke-ApiRequest -Method "GET" -Url $DashboardUrl

if ($DashboardResult.Success) {
    Write-Host "✅ Successfully retrieved dashboard data" -ForegroundColor Green
    Write-Host ($DashboardResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to get dashboard data: $($DashboardResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 8: Test Webhook Health Check" -ForegroundColor Yellow

$HealthUrl = "$BaseUrl/api/facebook-analytics/pages/$PageId/webhook-health"
$HealthResult = Invoke-ApiRequest -Method "GET" -Url $HealthUrl

if ($HealthResult.Success) {
    Write-Host "✅ Successfully retrieved webhook health" -ForegroundColor Green
    Write-Host ($HealthResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to get webhook health: $($HealthResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 9: Simulate Incoming Message" -ForegroundColor Yellow

$MessageUrl = "$BaseUrl/api/facebook/webhook"
$MessageBody = @{
    object = "page"
    entry = @(
        @{
            id = $PageId
            time = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
            messaging = @(
                @{
                    sender = @{ id = "test_user_123" }
                    recipient = @{ id = $PageId }
                    timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                    message = @{
                        mid = "test_message_id_123"
                        text = "Hello, I want to know about your products"
                    }
                }
            )
        }
    )
}

$MessageResult = Invoke-ApiRequest -Method "POST" -Url $MessageUrl -Body $MessageBody

if ($MessageResult.Success) {
    Write-Host "✅ Successfully processed incoming message" -ForegroundColor Green
    Write-Host ($MessageResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to process incoming message: $($MessageResult.Error)" -ForegroundColor Red
}

Write-Host "`n📋 Test 10: Clean Up - Remove Page" -ForegroundColor Yellow

$DeletePageUrl = "$BaseUrl/api/facebook-pages/shops/$ShopId/pages/$PageId"
$DeleteResult = Invoke-ApiRequest -Method "DELETE" -Url $DeletePageUrl

if ($DeleteResult.Success) {
    Write-Host "✅ Successfully removed Facebook page" -ForegroundColor Green
    Write-Host ($DeleteResult.Data | ConvertTo-Json -Depth 3) -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to remove Facebook page: $($DeleteResult.Error)" -ForegroundColor Red
}

Write-Host "`n🎯 Facebook Multiple Pages Integration Test Complete!" -ForegroundColor Green
Write-Host "Check the results above to verify all functionality is working correctly." -ForegroundColor Cyan

# Generate test report
$TestResults = @{
    TestSuite = "Facebook Multiple Pages Integration"
    Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    BaseUrl = $BaseUrl
    ShopId = $ShopId
    PageId = $PageId
    Tests = @(
        @{Name = "Add Page"; Success = $AddResult.Success}
        @{Name = "Get Pages"; Success = $GetPagesResult.Success}
        @{Name = "Get Page Config"; Success = $GetPageResult.Success}
        @{Name = "Update Page"; Success = $UpdateResult.Success}
        @{Name = "Webhook Verify"; Success = $WebhookResult.Success}
        @{Name = "Analytics"; Success = $AnalyticsResult.Success}
        @{Name = "Dashboard"; Success = $DashboardResult.Success}
        @{Name = "Health Check"; Success = $HealthResult.Success}
        @{Name = "Process Message"; Success = $MessageResult.Success}
        @{Name = "Remove Page"; Success = $DeleteResult.Success}
    )
}

$ReportFile = "facebook_test_report_$(Get-Date -Format 'yyyyMMdd_HHmmss').json"
$TestResults | ConvertTo-Json -Depth 10 | Out-File -FilePath $ReportFile -Encoding UTF8

Write-Host "`n📊 Test report saved to: $ReportFile" -ForegroundColor Magenta

$SuccessCount = ($TestResults.Tests | Where-Object {$_.Success}).Count
$TotalCount = $TestResults.Tests.Count
$SuccessRate = [math]::Round(($SuccessCount / $TotalCount) * 100, 2)

Write-Host "📈 Success Rate: $SuccessCount/$TotalCount ($SuccessRate%)" -ForegroundColor $(if ($SuccessRate -ge 80) { "Green" } else { "Red" })

if ($SuccessRate -eq 100) {
    Write-Host "🎉 All tests passed! Facebook multiple pages integration is ready for production." -ForegroundColor Green
} elseif ($SuccessRate -ge 80) {
    Write-Host "⚠️  Most tests passed. Review failed tests and fix issues." -ForegroundColor Yellow
} else {
    Write-Host "❌ Multiple tests failed. Please review and fix the implementation." -ForegroundColor Red
}
