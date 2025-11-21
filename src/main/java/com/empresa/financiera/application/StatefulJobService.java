package com.empresa.financiera.application;

import com.empresa.financiera.application.service.BusinessTaskService;
import com.empresa.financiera.domain.model.JobParameters;
import com.empresa.financiera.infrastructure.service.MockParameterService;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio que implementa el patrón de "Polling con Estado Compartido".
 * 
 * Utiliza un AtomicReference para compartir el estado de los parámetros del job
 * entre dos tareas programadas de forma thread-safe:
 * - Updater: Actualiza los parámetros periódicamente desde una fuente externa
 * - Worker: Ejecuta la lógica de negocio usando los parámetros compartidos
 */
@Slf4j
@ApplicationScoped
public class StatefulJobService {

    private static final String LOG_ESPERANDO_CONFIGURACION = "Esperando configuración...";
    private static final String LOG_EJECUTANDO_LOGICA = "Ejecutando lógica de negocio con ID: {}";

    /**
     * Estado compartido thread-safe que almacena los últimos parámetros obtenidos.
     * Inicializado como null hasta que el Updater obtenga los primeros parámetros.
     */
    private final AtomicReference<JobParameters> lastParams = new AtomicReference<>();

    private final MockParameterService mockParameterService;
    private final BusinessTaskService businessTaskService;

    /**
     * Constructor con inyección de dependencias.
     * 
     * @param mockParameterService Servicio para obtener parámetros de configuración
     * @param businessTaskService Servicio para ejecutar la lógica de negocio y persistir logs
     */
    @Inject
    public StatefulJobService(
            MockParameterService mockParameterService,
            BusinessTaskService businessTaskService) {
        this.mockParameterService = mockParameterService;
        this.businessTaskService = businessTaskService;
    }

    /**
     * Job 1 (Updater): Actualiza los parámetros de configuración cada 10 segundos.
     * 
     * Obtiene los parámetros desde MockParameterService y actualiza el estado compartido
     * de forma thread-safe usando AtomicReference.
     * 
     * Package-private para permitir invocación manual en tests de integración.
     * 
     * NOTA: No requiere @WithSession ya que no realiza operaciones de persistencia.
     * Solo actualiza el AtomicReference que es thread-safe.
     * 
     * @return Uni<Void> que completa cuando la actualización termina
     */
    @Scheduled(every = "10s")
    Uni<Void> updateParameters() {
        log.debug("Iniciando actualización de parámetros...");
        
        return mockParameterService.fetchParameters()
                .invoke(params -> {
                    lastParams.set(params);
                    log.info("Parámetros actualizados: interval={}, actionId={}", 
                            params.interval(), params.actionId());
                })
                .onFailure().invoke(throwable -> 
                    log.error("Error al actualizar parámetros", throwable)
                )
                .replaceWithVoid();
    }

    /**
     * Job 2 (Worker): Ejecuta la lógica de negocio cada 2 segundos.
     * 
     * Lee el estado compartido lastParams de forma thread-safe.
     * - Si es null: Retorna Uni<void> sin hacer nada (esperando configuración)
     * - Si hay datos: Ejecuta la lógica de negocio mediante BusinessTaskService,
     *   que persiste un log de ejecución en la base de datos de forma reactiva.
     * 
     * IMPORTANTE: 
     * - No requiere @WithSession ya que BusinessTaskService.process() usa
     *   Panache.withTransaction() que maneja correctamente el contexto reactivo.
     * - El método simplemente retorna el Uni resultante de businessTaskService.process()
     *   para que Quarkus maneje la suscripción y propague el contexto correctamente.
     * - NO se usa .subscribe() manual aquí.
     * - NO se toca ninguna entidad fuera del contexto de transacción manejado por
     *   BusinessTaskService, evitando así problemas de "Thread Drift".
     * 
     * @return Uni<Void> que completa cuando el procesamiento termina
     */
    @Scheduled(every = "2s")
    Uni<Void> executeWorkerLogic() {
        // Leer del AtomicReference (operación thread-safe, no requiere contexto reactivo)
        JobParameters params = lastParams.get();
        
        if (params == null) {
            log.info(LOG_ESPERANDO_CONFIGURACION);
            return Uni.createFrom().voidItem();
        }
        
        log.info(LOG_EJECUTANDO_LOGICA, params.actionId());
        
        // Retornar directamente el Uni de businessTaskService.process()
        // BusinessTaskService usa Panache.withTransaction() que garantiza que
        // toda la operación de persistencia ocurra en el event loop thread correcto
        return businessTaskService.process(params.actionId());
    }
}

