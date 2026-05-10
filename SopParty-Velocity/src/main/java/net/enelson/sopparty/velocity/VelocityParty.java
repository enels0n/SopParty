package net.enelson.sopparty.velocity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class VelocityParty {

    private final UUID id;
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<UUID>();

    VelocityParty(UUID id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.members.add(leader);
    }

    UUID getId() {
        return id;
    }

    UUID getLeader() {
        return leader;
    }

    void setLeader(UUID leader) {
        this.leader = leader;
    }

    Set<UUID> getMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<UUID>(members));
    }

    boolean addMember(UUID uuid) {
        return members.add(uuid);
    }

    boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    List<UUID> memberList() {
        return Collections.unmodifiableList(new ArrayList<UUID>(members));
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    int size() {
        return members.size();
    }
}
