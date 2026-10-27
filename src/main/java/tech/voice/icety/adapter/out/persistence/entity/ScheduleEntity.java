package tech.voice.icety.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "schedule")
public class ScheduleEntity {
    @Id
    private Short id;

    protected ScheduleEntity() { }
}
