package top.tobyprime.mcedia_mtv_plugin.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ManagedMtvPlayer {
    private UUID uuid;
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String mediaUrl = "";
    private float speed = 1.0F;
    private long startAt = 0L;
    private long baseTime = 0L;
    private long baseOffset = 0L;
    private boolean paused = false;
    private MtvChannelBinding channelBinding;
    private final List<ScreenPeripheralConfigModel> screens = new ArrayList<>();
    private final List<SpeakerPeripheralConfigModel> speakers = new ArrayList<>();

    public static ManagedMtvPlayer create(UUID uuid, String name, Location location) {
        var player = new ManagedMtvPlayer();
        player.uuid = uuid;
        player.name = name;
        player.captureLocation(location);
        player.channelBinding = MtvChannelBinding.self();
        player.screens.add(new ScreenPeripheralConfigModel("screen_0"));

        var leftSpeaker = new SpeakerPeripheralConfigModel("speaker_0");
        leftSpeaker.setChannelMode("left");
        player.speakers.add(leftSpeaker);

        var rightSpeaker = new SpeakerPeripheralConfigModel("speaker_1");
        rightSpeaker.setChannelMode("right");
        player.speakers.add(rightSpeaker);
        return player;
    }

    public void captureLocation(Location location) {
        this.world = location.getWorld() != null ? location.getWorld().getName() : null;
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public Location toLocation() {
        World bukkitWorld = world == null ? null : Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    // --- backward compat: get first peripheral ---

    public ScreenPeripheralConfigModel getScreen() {
        if (screens.isEmpty()) screens.add(new ScreenPeripheralConfigModel("screen_0"));
        return screens.get(0);
    }

    public SpeakerPeripheralConfigModel getSpeaker() {
        if (speakers.isEmpty()) speakers.add(new SpeakerPeripheralConfigModel("speaker_0"));
        return speakers.get(0);
    }

    // --- list access ---

    public List<ScreenPeripheralConfigModel> getScreens() { return screens; }
    public List<SpeakerPeripheralConfigModel> getSpeakers() { return speakers; }

    public ScreenPeripheralConfigModel findScreen(String id) {
        for (var s : screens) if (s.getId().equals(id)) return s;
        return null;
    }

    public SpeakerPeripheralConfigModel findSpeaker(String id) {
        for (var s : speakers) if (s.getId().equals(id)) return s;
        return null;
    }

    public ScreenPeripheralConfigModel addScreen() {
        String id = nextId("screen", screens.stream().map(ScreenPeripheralConfigModel::getId).toList());
        var s = new ScreenPeripheralConfigModel(id);
        screens.add(s);
        return s;
    }

    public SpeakerPeripheralConfigModel addSpeaker() {
        String id = nextId("speaker", speakers.stream().map(SpeakerPeripheralConfigModel::getId).toList());
        var s = new SpeakerPeripheralConfigModel(id);
        speakers.add(s);
        return s;
    }

    private static String nextId(String prefix, List<String> existing) {
        int i = 0;
        while (existing.contains(prefix + "_" + i)) i++;
        return prefix + "_" + i;
    }

    // --- timeline model ---

    /**
     * 根据时间线模型获取当前期望的播放位置（微秒）。
     * 暂停时位置冻结在 baseOffset（flattenTimeline 时拍平的值）。
     */
    public long getExpectedPosition() {
        if (paused) return baseOffset;
        long elapsed = System.currentTimeMillis() - baseTime;
        return baseOffset + (long)(elapsed * 1000L * speed);
    }

    /**
     * 将当前期望位置拍平到 baseOffset，baseTime 设为当前时间。
     * 在改速、暂停、seek 等操作前调用。
     */
    public void flattenTimeline() {
        long pos = getExpectedPosition();
        baseTime = System.currentTimeMillis();
        baseOffset = pos;
    }

    /**
     * 重置时间线到指定偏移位置，适用于新 URL 或 seek 操作。
     */
    public void resetTimeline(long offsetUs) {
        baseTime = System.currentTimeMillis();
        baseOffset = offsetUs;
        paused = false;
    }

    // --- getters / setters ---

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
    public long getStartAt() { return startAt; }
    public void setStartAt(long startAt) { this.startAt = startAt; }

    public long getBaseTime() { return baseTime; }
    public void setBaseTime(long baseTime) { this.baseTime = baseTime; }
    public long getBaseOffset() { return baseOffset; }
    public void setBaseOffset(long baseOffset) { this.baseOffset = baseOffset; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public MtvChannelBinding getChannelBinding() { return channelBinding; }
    public void setChannelBinding(MtvChannelBinding channelBinding) { this.channelBinding = channelBinding; }
}
