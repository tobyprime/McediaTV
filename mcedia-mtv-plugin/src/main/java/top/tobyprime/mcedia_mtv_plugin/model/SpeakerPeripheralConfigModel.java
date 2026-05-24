package top.tobyprime.mcedia_mtv_plugin.model;

public class SpeakerPeripheralConfigModel {
    private String id = "speaker_main";
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private float offsetRx;
    private float offsetRy;
    private float offsetRz;
    private float offsetRw = 1.0F;
    private float maxRange = 16.0F;
    private float volume = 1.0F;
    private String channelMode = "mix";

    public SpeakerPeripheralConfigModel() {
    }

    public SpeakerPeripheralConfigModel(String id) {
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
    public float getMaxRange() { return maxRange; }
    public void setMaxRange(float maxRange) { this.maxRange = maxRange; }
    public float getVolume() { return volume; }
    public void setVolume(float volume) { this.volume = volume; }
    public String getChannelMode() { return channelMode; }
    public void setChannelMode(String channelMode) { this.channelMode = channelMode; }

    // Group resets
    public void resetAudio() { volume = 1.0F; maxRange = 16.0F; channelMode = "mix"; }
    public void resetOffset() { offsetX = 0; offsetY = 0; offsetZ = 0; }
}
