package top.tobyprime.mcedia_mtv_plugin.model;

public class ScreenPeripheralConfigModel {
    private String id = "screen_main";
    private float offsetX;
    private float offsetY = 0.5F;
    private float offsetZ;
    private float offsetRx;
    private float offsetRy;
    private float offsetRz;
    private float offsetRw = 1.0F;
    private int minBrightness = 8;
    private float width = 2.0F;
    private float height = 1.125F;
    private String fillMode = "keep_aspect_cover";
    private String backgroundTexture = "mcedia:textures/gui/idle_screen.png";
    private boolean danmakuVisible = true;

    public ScreenPeripheralConfigModel() {
    }

    public ScreenPeripheralConfigModel(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public float getOffsetX() { return offsetX; }
    public void setOffsetX(float offsetX) { this.offsetX = offsetX; }
    public float getOffsetY() { return offsetY; }
    public void setOffsetY(float offsetY) { this.offsetY = offsetY; }
    public float getOffsetZ() { return offsetZ; }
    public void setOffsetZ(float offsetZ) { this.offsetZ = offsetZ; }
    public float getOffsetRx() { return offsetRx; }
    public void setOffsetRx(float offsetRx) { this.offsetRx = offsetRx; }
    public float getOffsetRy() { return offsetRy; }
    public void setOffsetRy(float offsetRy) { this.offsetRy = offsetRy; }
    public float getOffsetRz() { return offsetRz; }
    public void setOffsetRz(float offsetRz) { this.offsetRz = offsetRz; }
    public float getOffsetRw() { return offsetRw; }
    public void setOffsetRw(float offsetRw) { this.offsetRw = offsetRw; }
    public int getMinBrightness() { return minBrightness; }
    public void setMinBrightness(int minBrightness) { this.minBrightness = minBrightness; }
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }
    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }
    public String getFillMode() { return fillMode; }
    public void setFillMode(String fillMode) { this.fillMode = fillMode; }
    public String getBackgroundTexture() { return backgroundTexture; }
    public void setBackgroundTexture(String backgroundTexture) { this.backgroundTexture = backgroundTexture; }
    public boolean isDanmakuVisible() { return danmakuVisible; }
    public void setDanmakuVisible(boolean danmakuVisible) { this.danmakuVisible = danmakuVisible; }

    // Group resets
    public void resetSize() { width = 2.0F; height = 1.125F; }
    public void resetBasic() { minBrightness = 8; fillMode = "keep_aspect_cover"; backgroundTexture = "mcedia:textures/gui/idle_screen.png"; }
    public void resetOffset() { offsetX = 0; offsetY = 0.5F; offsetZ = 0; }
    public void resetRotation() { offsetRx = 0; offsetRy = 0; offsetRz = 0; offsetRw = 1; }
}
