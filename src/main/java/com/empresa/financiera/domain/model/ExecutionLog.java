package com.empresa.financiera.domain.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entidad que representa un log de ejecución de una tarea de negocio.
 * 
 * Almacena información sobre las ejecuciones realizadas por el Worker,
 * incluyendo los parámetros utilizados y el timestamp de la ejecución.
 */
@Entity
@Table(name = "execution_logs")
public class ExecutionLog extends PanacheEntity {

    /**
     * Parámetros utilizados en la ejecución (formato: actionId del JobParameters).
     */
    @Column(name = "params_used", nullable = false, length = 255)
    public String paramsUsed;

    /**
     * Timestamp de cuando se ejecutó la tarea.
     */
    @Column(name = "timestamp", nullable = false)
    public LocalDateTime timestamp;
}

