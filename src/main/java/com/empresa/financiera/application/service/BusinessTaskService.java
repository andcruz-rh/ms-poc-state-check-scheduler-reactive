package com.empresa.financiera.application.service;

import com.empresa.financiera.domain.model.ExecutionLog;
import com.empresa.financiera.infrastructure.repository.ExecutionLogRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Servicio que encapsula la lógica de negocio para procesar tareas.
 * 
 * Persiste logs de ejecución en la base de datos de forma reactiva,
 * validando que el contexto de persistencia se propaga correctamente
 * desde los métodos @Scheduled.
 */
@Slf4j
@ApplicationScoped
public class BusinessTaskService {

    private static final String LOG_PERSISTIENDO_EJECUCION = "Persistiendo log de ejecución con parámetros: {}";

    private final ExecutionLogRepository executionLogRepository;

    /**
     * Constructor con inyección de dependencias.
     * 
     * @param executionLogRepository Repositorio para persistir logs de ejecución
     */
    @Inject
    public BusinessTaskService(ExecutionLogRepository executionLogRepository) {
        this.executionLogRepository = executionLogRepository;
    }

    /**
     * Procesa una tarea de negocio y persiste un log de ejecución.
     * 
     * Crea un nuevo ExecutionLog con los parámetros recibidos y lo persiste
     * en la base de datos de forma reactiva. Este método valida que el contexto
     * de persistencia se propaga correctamente desde los métodos @Scheduled.
     * 
     * IMPORTANTE: Usa Panache.withTransaction() para garantizar que toda la
     * operación de persistencia ocurra dentro del contexto reactivo correcto
     * (vert.x event loop thread), evitando problemas de "Thread Drift" cuando
     * se invoca desde métodos @Scheduled que se ejecutan en executor-threads.
     * 
     * @param params Parámetros utilizados en la ejecución (actionId del JobParameters)
     * @return Uni<Void> que completa cuando la persistencia termina exitosamente
     */
    public Uni<Void> process(String params) {
        log.debug(LOG_PERSISTIENDO_EJECUCION, params);
        
        // Usar Panache.withTransaction() para delimitar explícitamente el alcance
        // de la sesión reactiva y asegurar que toda la operación ocurra en el
        // event loop thread correcto, evitando "Thread Drift"
        return Panache.withTransaction(() -> {
            // Crear la entidad dentro del bloque de transacción
            ExecutionLog executionLog = new ExecutionLog();
            executionLog.paramsUsed = params;
            executionLog.timestamp = LocalDateTime.now();
            
            // Persistir dentro del bloque de transacción
            // IMPORTANTE: No tocar executionLog fuera de este bloque
            return executionLogRepository.persist(executionLog)
                    .invoke(() -> log.info("Log de ejecución persistido exitosamente: actionId={}, timestamp={}", 
                            params, executionLog.timestamp))
                    .onFailure().invoke(throwable -> 
                        log.error("Error al persistir log de ejecución con parámetros: {}", params, throwable)
                    )
                    .replaceWithVoid();
        });
    }
}

