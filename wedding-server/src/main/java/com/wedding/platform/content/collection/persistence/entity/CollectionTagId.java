package com.wedding.platform.content.collection.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class CollectionTagId implements Serializable {

    @Column(name = "collection_id")
    private Long collectionId;

    @Column(name = "tag_id")
    private Long tagId;
}
