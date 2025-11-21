package com.empresa.financiera.infrastructure.repository;

import com.empresa.financiera.domain.model.ExecutionLog;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositorio reactivo para la entidad ExecutionLog.
 * 
 * Proporciona métodos de acceso a datos reactivos usando Panache Reactive.
 * Extiende PanacheRepository para obtener funcionalidades CRUD básicas de forma reactiva.
 */
@ApplicationScoped
public class ExecutionLogRepository implements PanacheRepository<ExecutionLog> {
    // Panache Reactive proporciona automáticamente métodos como:
    // - persist(), persistAndFlush()
    // - findAll(), findById()
    // - delete(), deleteAll()
    // Todos retornando Uni o Multi según corresponda
}

