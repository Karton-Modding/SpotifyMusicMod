# Media control helper. Reads one command per line on stdin and drives the Windows media session.
# Commands: next, prev, playpause, play, pause, volup, voldown, quit
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -MemberDefinition '[DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, System.UIntPtr dwExtraInfo);' -Name Keys -Namespace Spw

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($operation, $resultType) {
    $method = $asTask.MakeGenericMethod($resultType)
    $task = $method.Invoke($null, @($operation))
    if (-not $task.Wait(4000)) { return $null }
    return $task.Result
}

function Emit($text) {
    [Console]::Out.WriteLine($text)
    [Console]::Out.Flush()
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$manager = Await ($managerType::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
if ($null -eq $manager) {
    Emit 'error no session manager'
    exit 1
}

$preferSpotify = ($env:SPW_PREFER_SPOTIFY -eq '1')

function Get-Session {
    foreach ($candidate in $manager.GetSessions()) {
        if ($candidate.SourceAppUserModelId -like '*spotify*') { return $candidate }
    }
    if ($preferSpotify) { return $null }
    return $manager.GetCurrentSession()
}

function Tap([byte] $key) {
    [Spw.Keys]::keybd_event($key, 0, 0, [UIntPtr]::Zero)          # key down
    [Spw.Keys]::keybd_event($key, 0, 2, [UIntPtr]::Zero)          # key up
}

Emit 'ready'

while ($true) {
    $line = [Console]::In.ReadLine()
    if ($null -eq $line) { break }
    $command = $line.Trim().ToLowerInvariant()
    if ($command -eq '') { continue }
    if ($command -eq 'quit') { break }

    try {
        $handled = $true
        switch ($command) {
            'volup'   { Tap 0xAF }
            'voldown' { Tap 0xAE }
            default {
                $session = Get-Session
                if ($null -eq $session) {
                    Emit "miss $command"
                    $handled = $false
                } else {
                    switch ($command) {
                        'next'      { Await ($session.TrySkipNextAsync()) ([bool]) | Out-Null }
                        'prev'      { Await ($session.TrySkipPreviousAsync()) ([bool]) | Out-Null }
                        'playpause' { Await ($session.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
                        'play'      { Await ($session.TryPlayAsync()) ([bool]) | Out-Null }
                        'pause'     { Await ($session.TryPauseAsync()) ([bool]) | Out-Null }
                        default     { Emit "unknown $command"; $handled = $false }
                    }
                }
            }
        }
        if ($handled) { Emit "ok $command" }
    } catch {
        Emit "fail $command"
    }
}
