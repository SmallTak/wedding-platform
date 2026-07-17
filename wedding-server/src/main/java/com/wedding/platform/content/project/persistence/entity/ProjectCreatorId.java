package com.wedding.platform.content.project.persistence.entity;

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
public class ProjectCreatorId implements Serializable {

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "creator_user_id")
    private Long creatorUserId;
}
