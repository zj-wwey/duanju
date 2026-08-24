# Currency Conversion Verification Script
# Usage: powershell -ExecutionPolicy Bypass -File test-currency-conversion.ps1
# Prerequisite: backend deployed with new PointProductController + CurrencyRateServiceImpl

$baseUrl = "http://localhost:8080"

# Expected values for product id=1 (100 points, priceCents=199 USD)
$testLocales = @{
    "en"       = @{ Currency = "USD"; Price = 199 }
    "zh-Hans"  = @{ Currency = "CNY"; Price = 1443 }
    "zh-Hant"  = @{ Currency = "TWD"; Price = 65 }
    "ja"       = @{ Currency = "JPY"; Price = 299 }
    "ko"       = @{ Currency = "KRW"; Price = 2687 }
    "th"       = @{ Currency = "THB"; Price = 7065 }
    "vi"       = @{ Currency = "VND"; Price = 50546 }
    "id"       = @{ Currency = "IDR"; Price = 33432 }
    "ms"       = @{ Currency = "MYR"; Price = 935 }
    "es"       = @{ Currency = "EUR"; Price = 183 }
    "fr"       = @{ Currency = "EUR"; Price = 183 }
    "de"       = @{ Currency = "EUR"; Price = 183 }
    "it"       = @{ Currency = "EUR"; Price = 183 }
    "pt"       = @{ Currency = "BRL"; Price = 1015 }
    "ru"       = @{ Currency = "RUB"; Price = 18308 }
    "tr"       = @{ Currency = "TRY"; Price = 6468 }
    "ar"       = @{ Currency = "AED"; Price = 730 }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Currency Conversion Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: No X-Locale header (should default to USD)
Write-Host "--- Test 1: No X-Locale (expect USD fallback) ---" -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod -Uri "$baseUrl/api/point-products" -Method Get
    $product = $resp.data[0]
    $hasLocal = $product.PSObject.Properties.Name -contains "localPriceCents"
    if (-not $hasLocal) {
        Write-Host "  [SKIP] localPriceCents field missing - backend not redeployed yet" -ForegroundColor Magenta
        Write-Host "  Current response: currency=$($product.currency) priceCents=$($product.price_cents)" -ForegroundColor DarkGray
    } elseif ($product.currency -eq "USD") {
        Write-Host "  [PASS] Default returns USD" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected USD, got $($product.currency)" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] Request failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test 2: Verify each locale
Write-Host "--- Test 2: Per-Locale Conversion ---" -ForegroundColor Yellow
$passCount = 0
$failCount = 0
$skipCount = 0

foreach ($locale in ($testLocales.Keys | Sort-Object)) {
    $expected = $testLocales[$locale]
    Write-Host -NoNewline "  [$locale]"

    try {
        $headers = @{ "X-Locale" = $locale }
        $resp = Invoke-RestMethod -Uri "$baseUrl/api/point-products" -Headers $headers -Method Get
        $product = $resp.data[0]

        $hasLocal = $product.PSObject.Properties.Name -contains "localPriceCents"
        if (-not $hasLocal) {
            Write-Host " [SKIP] no localPriceCents field" -ForegroundColor Magenta
            $skipCount++
            continue
        }

        $actualCurrency = $product.currency
        $actualPrice = $product.localPriceCents

        # Check currency
        $currencyOk = $actualCurrency -eq $expected.Currency

        # Check price within 5% tolerance
        $diff = [Math]::Abs($actualPrice - $expected.Price)
        $tolerance = [Math]::Max(1, [int]($expected.Price * 0.05))
        $priceOk = $diff -le $tolerance

        if ($currencyOk -and $priceOk) {
            Write-Host " [PASS] currency=$actualCurrency localPrice=$actualPrice (expected ~$($expected.Price))" -ForegroundColor Green
            $passCount++
        } else {
            $issues = @()
            if (-not $currencyOk) { $issues += "currency: expected $($expected.Currency), got $actualCurrency" }
            if (-not $priceOk) { $issues += "price: expected ~$($expected.Price), got $actualPrice (diff=$diff)" }
            Write-Host " [FAIL] $($issues -join '; ')" -ForegroundColor Red
            $failCount++
        }
    } catch {
        Write-Host " [FAIL] Request error: $_" -ForegroundColor Red
        $failCount++
    }
}

Write-Host ""
Write-Host "--- Summary ---" -ForegroundColor Yellow
Write-Host "  PASS: $passCount  FAIL: $failCount  SKIP: $skipCount" -ForegroundColor $(if ($failCount -eq 0 -and $skipCount -eq 0) { "Green" } else { "Yellow" })
Write-Host ""

# Test 3: Compare same product across locales
Write-Host "--- Test 3: Cross-Locale Comparison (product id=3, 1200pts, 1499 USD) ---" -ForegroundColor Yellow
$compareLocales = @("en", "zh-Hans", "ja", "es", "ar")
$results = @()

foreach ($locale in $compareLocales) {
    try {
        $headers = @{ "X-Locale" = $locale }
        $resp = Invoke-RestMethod -Uri "$baseUrl/api/point-products" -Headers $headers -Method Get
        $product = $resp.data | Where-Object { $_.id -eq 3 }
        if ($product) {
            $hasLocal = $product.PSObject.Properties.Name -contains "localPriceCents"
            $results += [PSCustomObject]@{
                Locale    = $locale
                Currency  = $product.currency
                Local     = if ($hasLocal) { $product.localPriceCents } else { "N/A" }
                Original  = $product.price_cents
            }
        }
    } catch {}
}

if ($results.Count -gt 0) {
    $results | Format-Table -AutoSize
} else {
    Write-Host "  No data returned" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Done" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
