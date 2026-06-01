package top.tobyprime.mcedia_mtv_plugin.selector;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MtvPlayerSelector {
    private final JavaPlugin plugin;
    private final MtvPlayerManager manager;

    public MtvPlayerSelector(JavaPlugin plugin, MtvPlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void findNearbyAsync(Player player, double range, Consumer<List<ManagedMtvPlayer>> done) {
        runOnPlayer(player, () -> scanNearby(player, range, done));
    }

    public void findNearbyChannelAsync(Player player, String channelId, double range, Consumer<ManagedMtvPlayer> done) {
        if (channelId == null || channelId.isBlank()) {
            done.accept(null);
            return;
        }
        runOnPlayer(player, () -> scanNearbyChannel(player, channelId, range, done));
    }

    private void scanNearby(Player player, double range, Consumer<List<ManagedMtvPlayer>> done) {
        double rangeSq = range * range;
        var origin = player.getLocation();
        var candidates = player.getNearbyEntities(range, range, range).stream()
                .filter(ItemDisplay.class::isInstance)
                .map(ItemDisplay.class::cast)
                .toList();

        if (candidates.isEmpty()) {
            done.accept(List.of());
            return;
        }

        var result = new ConcurrentLinkedQueue<ManagedMtvPlayer>();
        var remaining = new AtomicInteger(candidates.size());

        for (var display : candidates) {
            display.getScheduler().run(plugin, task -> {
                if (manager.isManagedItemDisplay(display)) {
                    var snapshot = manager.readFromEntity(display);
                    if (sameWorld(origin, snapshot) && distanceSquared(origin, snapshot) <= rangeSq) {
                        result.add(snapshot);
                    }
                }
                onNearbySnapshotDone(player, origin, result, remaining, done);
            }, () -> onNearbySnapshotDone(player, origin, result, remaining, done));
        }
    }

    private void scanNearbyChannel(Player player, String channelId, double range, Consumer<ManagedMtvPlayer> done) {
        double rangeSq = range * range;
        var origin = player.getLocation();
        var candidates = player.getNearbyEntities(range, range, range).stream()
                .filter(ItemDisplay.class::isInstance)
                .map(ItemDisplay.class::cast)
                .toList();

        if (candidates.isEmpty()) {
            done.accept(null);
            return;
        }

        var result = new ConcurrentLinkedQueue<ManagedMtvPlayer>();
        var remaining = new AtomicInteger(candidates.size());

        for (var display : candidates) {
            display.getScheduler().run(plugin, task -> {
                if (manager.isManagedItemDisplay(display)) {
                    var snapshot = manager.readFromEntity(display);
                    if (sameWorld(origin, snapshot)
                            && distanceSquared(origin, snapshot) <= rangeSq
                            && channelId.equals(manager.getChannelService().resolveBinding(snapshot).channelId())) {
                        result.add(snapshot);
                    }
                }
                onNearbyChannelDone(player, origin, result, remaining, done);
            }, () -> onNearbyChannelDone(player, origin, result, remaining, done));
        }
    }

    private void onNearbySnapshotDone(Player player,
                                      Location origin,
                                      ConcurrentLinkedQueue<ManagedMtvPlayer> result,
                                      AtomicInteger remaining,
                                      Consumer<List<ManagedMtvPlayer>> done) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        runOnPlayer(player, () -> {
            var sorted = new ArrayList<>(result);
            sorted.sort(Comparator.comparingDouble(snapshot -> distanceSquared(origin, snapshot)));
            done.accept(sorted);
        });
    }

    private void onNearbyChannelDone(Player player,
                                     Location origin,
                                     ConcurrentLinkedQueue<ManagedMtvPlayer> result,
                                     AtomicInteger remaining,
                                     Consumer<ManagedMtvPlayer> done) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        runOnPlayer(player, () -> done.accept(result.stream()
                .min(Comparator.comparingDouble(snapshot -> distanceSquared(origin, snapshot)))
                .orElse(null)));
    }

    private void runOnPlayer(Player player, Runnable action) {
        if (player == null || action == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> action.run(), null);
    }

    private static boolean sameWorld(Location origin, ManagedMtvPlayer snapshot) {
        return origin.getWorld() != null
                && snapshot.getWorld() != null
                && origin.getWorld().getName().equals(snapshot.getWorld());
    }

    private static double distanceSquared(Location origin, ManagedMtvPlayer snapshot) {
        double dx = origin.getX() - snapshot.getX();
        double dy = origin.getY() - snapshot.getY();
        double dz = origin.getZ() - snapshot.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
