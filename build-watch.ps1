while ($true) {
    Get-ChildItem -Path . -Filter "*.kt" -Recurse | ForEach-Object {
        $lastWriteTime = $_.LastWriteTime
        if ($lastWriteTime -gt $global:lastBuildTime) {
            Write-Host "Change detected in $($_.FullName)"
            $global:lastBuildTime = Get-Date
            & .\gradlew.bat build
        }
    }
    Start-Sleep -Seconds 2  # Adjust for file polling frequency
}

