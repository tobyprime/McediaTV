package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChannelBindingRegistry {
    private final Map<UUID, MtvChannelBinding> entityBindings = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> channelMembers = new ConcurrentHashMap<>();

    public MtvChannelBinding bind(UUID entityUuid, MtvChannelBinding binding) {
        var previous = entityBindings.put(entityUuid, binding);
        if (previous != null && !previous.channelId().equals(binding.channelId())) {
            removeMember(previous.channelId(), entityUuid);
        }
        channelMembers.computeIfAbsent(binding.channelId(), key -> ConcurrentHashMap.newKeySet()).add(entityUuid);
        return previous;
    }

    public MtvChannelBinding get(UUID entityUuid) {
        return entityBindings.get(entityUuid);
    }

    public List<UUID> getMembers(String channelId) {
        var members = channelMembers.get(channelId);
        return members == null ? List.of() : List.copyOf(members);
    }

    public boolean unbind(UUID entityUuid) {
        var binding = entityBindings.remove(entityUuid);
        if (binding == null) {
            return false;
        }
        removeMember(binding.channelId(), entityUuid);
        return true;
    }

    public boolean hasMembers(String channelId) {
        var members = channelMembers.get(channelId);
        return members != null && !members.isEmpty();
    }

    private void removeMember(String channelId, UUID entityUuid) {
        var members = channelMembers.get(channelId);
        if (members == null) {
            return;
        }
        members.remove(entityUuid);
        if (members.isEmpty()) {
            channelMembers.remove(channelId);
        }
    }
}
