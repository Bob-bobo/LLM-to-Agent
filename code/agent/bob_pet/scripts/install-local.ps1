# Install BobPet portable build to user Programs folder
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Source = Join-Path $Root "dist\win-unpacked"
$Target = Join-Path $env:LOCALAPPDATA "Programs\BobPet"

if (-not (Test-Path (Join-Path $Source "BobPet.exe"))) {
  Write-Host "Run first: npm run build:dir"
  exit 1
}

Write-Host "Installing BobPet to $Target ..."
if (Test-Path $Target) { Remove-Item $Target -Recurse -Force }
New-Item -ItemType Directory -Path $Target -Force | Out-Null
Copy-Item -Path "$Source\*" -Destination $Target -Recurse

$Wsh = New-Object -ComObject WScript.Shell
$Desktop = [Environment]::GetFolderPath("Desktop")
$Shortcut = $Wsh.CreateShortcut((Join-Path $Desktop "BobPet.lnk"))
$Shortcut.TargetPath = Join-Path $Target "BobPet.exe"
$Shortcut.WorkingDirectory = $Target
$Shortcut.Description = "BobPet Desktop Pet"
$Shortcut.Save()

Write-Host "Done. Desktop shortcut created."
Write-Host "Run: $(Join-Path $Target 'BobPet.exe')"
