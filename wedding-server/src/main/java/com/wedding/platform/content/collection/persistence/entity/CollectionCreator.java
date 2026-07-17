package com.wedding.platform.content.collection.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "collection_creator")
public class CollectionCreator {

    @EmbeddedId
    private CollectionCreatorId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @PrePersist
    void prePersist() {
        joinedAt = joinedAt == null ? Instant.now() : joinedAt;
    }
}
