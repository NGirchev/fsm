package io.github.ngirchev.fsm.example.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ngirchev.fsm.spring.definition.FlowVersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "fsm_flow_version")
@NoArgsConstructor
public class FlowVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_key", nullable = false, length = 120)
    private String flowKey;

    @Column(nullable = false)
    private int version;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlowVersionStatus status;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode definition;

    public FlowVersionEntity(String flowKey, int version, JsonNode definition) {
        this.flowKey = flowKey;
        this.version = version;
        this.status = FlowVersionStatus.DRAFT;
        this.definition = definition;
    }
}
