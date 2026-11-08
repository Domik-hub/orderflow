param([string]$BaseUrl = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
function Invoke-Api([string]$Method, [string]$Path, $Body, [string]$Token, [string]$Key = '') {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($Key) { $headers['Idempotency-Key'] = $Key }
    $request = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; ContentType = 'application/json' }
    if ($null -ne $Body) { $request.Body = $Body | ConvertTo-Json -Compress }
    Invoke-RestMethod @request
}
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$admin = (Invoke-Api POST '/api/auth/login' @{email='admin@example.com';password='OrderFlowDemo2026!'} '').accessToken
$product = Invoke-Api POST '/api/admin/products' @{sku="DEMO-$($suffix.ToUpper())";name='Demo keyboard';price=12500.50;stock=3} $admin
$email = "buyer-$suffix@example.com"
$null = Invoke-Api POST '/api/auth/register' @{email=$email;displayName='Demo Buyer';password='BuyerPassword2026!'} ''
$buyer = (Invoke-Api POST '/api/auth/login' @{email=$email;password='BuyerPassword2026!'} '').accessToken
$null = Invoke-Api PUT "/api/cart/items/$($product.id)" @{quantity=2} $buyer
$key = [Guid]::NewGuid().ToString()
$body = @{shippingAddress='Astana demonstration address'}
$order = Invoke-Api POST '/api/orders' $body $buyer $key
if ([decimal]$order.total -ne 25001.00) { throw 'Unexpected total.' }
Write-Host "Created order $($order.id), total $($order.total) KZT."
$replay = Invoke-Api POST '/api/orders' $body $buyer $key
if ($replay.id -ne $order.id) { throw 'Retry created a different order.' }
Write-Host 'Retry returned the same order.'
$stock = Invoke-Api GET "/api/products/$($product.id)/stock" $null $buyer
if ($stock.available -ne 1) { throw 'Unexpected stock after checkout.' }
$null = Invoke-Api POST "/api/orders/$($order.id)/cancel" $null $buyer
$null = Invoke-Api POST "/api/orders/$($order.id)/cancel" $null $buyer
$stock = Invoke-Api GET "/api/products/$($product.id)/stock" $null $buyer
if ($stock.available -ne 3) { throw 'Cancellation did not restore stock exactly once.' }
Write-Host 'Cancellation restored all three units; repeated cancellation did not add extra stock.'
