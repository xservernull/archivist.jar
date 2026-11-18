// provides the base class for entities that are assigned to and tracked by a player.

package com.xservernull.archivist.entity;

import java.util.Optional;
import java.util.UUID;

public interface Owned {

    Optional<UUID> owner();

    default boolean isOwnedBy(UUID id) {
        return owner().filter(id::equals).isPresent();
    }
}
