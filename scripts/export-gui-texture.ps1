# Export artwork uniformly, then fit the generated hotbar rail into the native slot gutter.
# Base geometry stays intact; only the rail and complete lower sill use the detail atlas.
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
function Export-UniformBitmap([string] $path) {
    $source = [System.Drawing.Bitmap]::new($path)
    $bitmap = [System.Drawing.Bitmap]::new(704, 552)
    $canvas = [System.Drawing.Graphics]::FromImage($bitmap)
    $canvas.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $canvas.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    try {
        $scale = [Math]::Max(704.0 / $source.Width, 552.0 / $source.Height)
        $width = 704.0 / $scale; $height = 552.0 / $scale
        $crop = [System.Drawing.RectangleF]::new(($source.Width-$width)/2, ($source.Height-$height)/2, $width, $height)
        $canvas.DrawImage($source, [System.Drawing.RectangleF]::new(0,0,704,552), $crop, [System.Drawing.GraphicsUnit]::Pixel)
    } finally { $canvas.Dispose(); $source.Dispose() }
    return $bitmap
}
$target = Export-UniformBitmap (Join-Path $projectRoot 'resource-pack/source/corpse-panel.png')
$details = Export-UniformBitmap (Join-Path $projectRoot 'resource-pack/source/corpse-panel-details.png')
$graphics = [System.Drawing.Graphics]::FromImage($target)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
try {
    # Inventory items occupy y360..423 and y432..495, leaving exactly this gutter free.
    $graphics.DrawImage($details, [System.Drawing.RectangleF]::new(0,424,704,8), [System.Drawing.RectangleF]::new(0,405,704,20), [System.Drawing.GraphicsUnit]::Pixel)
    # One continuous lower sill carries the square teal gem without a separate inset patch.
    $graphics.DrawImage($details, [System.Drawing.RectangleF]::new(0,500,704,52), [System.Drawing.RectangleF]::new(0,500,704,52), [System.Drawing.GraphicsUnit]::Pixel)
    $target.Save((Join-Path $projectRoot 'resource-pack/assets/tracesdeath/textures/font/corpse_panel.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $target.Dispose(); $details.Dispose() }
