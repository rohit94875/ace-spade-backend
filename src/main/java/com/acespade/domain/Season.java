package com.acespade.domain;

import com.acespade.model.enums.SeasonStatus;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "seasons")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeasonStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "grace_ends_at", nullable = false)
    private Instant graceEndsAt;

    @Column(name = "rewards_tracked", nullable = false)
    private boolean rewardsTracked = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SeasonStatus getStatus() { return status; }
    public void setStatus(SeasonStatus status) { this.status = status; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public Instant getGraceEndsAt() { return graceEndsAt; }
    public void setGraceEndsAt(Instant graceEndsAt) { this.graceEndsAt = graceEndsAt; }
    public boolean isRewardsTracked() { return rewardsTracked; }
    public void setRewardsTracked(boolean rewardsTracked) { this.rewardsTracked = rewardsTracked; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
