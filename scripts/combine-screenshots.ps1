# Join the original gameplay screenshots without cropping or changing their contents.
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
foreach ($mode in @('vanilla', 'resourcepack')) {
    $leftImage = [System.Drawing.Image]::FromFile((Join-Path $projectRoot "assets/screenshots/tombstone-$mode.png"))
    $rightImage = [System.Drawing.Image]::FromFile((Join-Path $projectRoot "assets/screenshots/gui-$mode.png"))
    try {
        $height = 600
        $leftWidth = [int][Math]::Round($leftImage.Width * $height / $leftImage.Height)
        $rightWidth = [int][Math]::Round($rightImage.Width * $height / $rightImage.Height)
        $combined = New-Object System.Drawing.Bitmap ($leftWidth + $rightWidth + 12), $height
        $graphics = [System.Drawing.Graphics]::FromImage($combined)
        try {
            $graphics.Clear([System.Drawing.Color]::FromArgb(34, 34, 34))
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($leftImage, 0, 0, $leftWidth, $height)
            $graphics.DrawImage($rightImage, ($leftWidth + 12), 0, $rightWidth, $height)
            $combined.Save((Join-Path $projectRoot "assets/screenshots/showcase-$mode.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
            $combined.Dispose()
        }
    } finally {
        $leftImage.Dispose()
        $rightImage.Dispose()
    }
}
