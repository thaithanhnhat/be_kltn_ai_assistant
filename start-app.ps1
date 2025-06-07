# Start Spring Boot Application Script
Write-Host "=== Starting Spring Boot Application ===" -ForegroundColor Green

# Check if Maven is available
try {
    $mvnVersion = mvn --version
    Write-Host "✅ Maven is available" -ForegroundColor Green
} catch {
    Write-Host "❌ Maven not found in PATH" -ForegroundColor Red
    Write-Host "Please install Maven or add it to PATH" -ForegroundColor Yellow
    exit 1
}

# Navigate to project directory
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectDir

Write-Host "Starting Spring Boot application..." -ForegroundColor Yellow
Write-Host "Project directory: $projectDir" -ForegroundColor Cyan

# Start the application
try {
    Write-Host "`nStarting with: mvn spring-boot:run" -ForegroundColor Yellow
    Write-Host "The application will start on port 8080" -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to stop the application" -ForegroundColor Yellow
    Write-Host "─" * 50 -ForegroundColor Gray
    
    mvn spring-boot:run
} catch {
    Write-Host "❌ Failed to start application" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
