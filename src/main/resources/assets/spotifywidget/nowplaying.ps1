# Reads the Windows "now playing" session (SMTC) and prints one JSON line per poll.
# Started by the mod; SPW_INTERVAL and SPW_PREFER_SPOTIFY come in as environment variables.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($operation, $resultType) {
    $method = $asTask.MakeGenericMethod($resultType)
    $task = $method.Invoke($null, @($operation))
    if (-not $task.Wait(5000)) { return $null }
    return $task.Result
}

function Emit($text) {
    [Console]::Out.WriteLine($text)
    [Console]::Out.Flush()
}

$interval = 1000
if ($env:SPW_INTERVAL) { $interval = [int]$env:SPW_INTERVAL }
$preferSpotify = ($env:SPW_PREFER_SPOTIFY -eq '1')

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$manager = Await ($managerType::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $manager) {
    Emit '{"state":"error","message":"no media session manager"}'
    exit 1
}

while ($true) {
    try {
        $session = $null
        foreach ($candidate in $manager.GetSessions()) {
            if ($candidate.SourceAppUserModelId -like '*spotify*') { $session = $candidate; break }
        }
        if ($null -eq $session -and -not $preferSpotify) { $session = $manager.GetCurrentSession() }

        if ($null -eq $session) {
            Emit '{"state":"none"}'
        } else {
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            if ($null -eq $props) {
                Emit '{"state":"none"}'
            } else {
                $payload = [ordered]@{
                    state    = 'ok'
                    app      = [string]$session.SourceAppUserModelId
                    title    = [string]$props.Title
                    artist   = [string]$props.Artist
                    album    = [string]$props.AlbumTitle
                    status   = [string]$playback.PlaybackStatus
                    position = [long]$timeline.Position.TotalMilliseconds
                    duration = [long]$timeline.EndTime.TotalMilliseconds
                }
                Emit ($payload | ConvertTo-Json -Compress)
            }
        }
    } catch {
        Emit '{"state":"error","message":"read failed"}'
    }
    Start-Sleep -Milliseconds $interval
}
