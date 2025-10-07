# LRU Cache Build and Test Script

# Clean and compile
Remove-Item -Recurse -Force target -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force target\classes | Out-Null
New-Item -ItemType Directory -Force target\test-classes | Out-Null

Write-Host "Compiling source files..." -ForegroundColor Green
javac -d target\classes src\main\java\com\lru\*.java

Write-Host "Compiling test files..." -ForegroundColor Green  
javac -cp target\classes -d target\test-classes src\test\java\com\lru\*.java

Write-Host "`nRunning demonstration..." -ForegroundColor Cyan
java -cp target\classes com.lru.CacheDemo

Write-Host "`nRunning test suite..." -ForegroundColor Cyan
java -cp "target\classes;target\test-classes" com.lru.ManualTestRunner

Write-Host "`nRunning performance benchmarks..." -ForegroundColor Cyan
java -cp target\classes com.lru.CacheBenchmark

Write-Host "`nBuild and test completed successfully!" -ForegroundColor Green