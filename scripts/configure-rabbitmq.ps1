param(
    [string]$Container = 'sports-booking-rabbitmq',
    [string]$VirtualHost = '/',
    [ValidateRange(1, 2147483647)][int]$MaxMessages,
    [ValidateRange(1, 2147483647)][int]$MaxBytes
)
$ErrorActionPreference = 'Stop'
$policy = Get-Content -Raw (Join-Path $PSScriptRoot '../docker/rabbitmq/backlog-policy.json') | ConvertFrom-Json
if ($PSBoundParameters.ContainsKey('MaxMessages')) { $policy.definition.'max-length' = $MaxMessages }
if ($PSBoundParameters.ContainsKey('MaxBytes')) { $policy.definition.'max-length-bytes' = $MaxBytes }
$definition = $policy.definition | ConvertTo-Json -Compress
# Policy updates existing queues without deleting or redeclaring their immutable arguments.
docker exec $Container rabbitmqctl set_policy -p $VirtualHost --priority $policy.priority --apply-to $policy.'apply-to' sports-booking-bounded $policy.pattern $definition
if ($LASTEXITCODE -ne 0) { throw 'Could not apply RabbitMQ backlog policy' }
docker exec $Container rabbitmqctl list_policies -p $VirtualHost
if ($LASTEXITCODE -ne 0) { throw 'Could not verify RabbitMQ backlog policy' }
