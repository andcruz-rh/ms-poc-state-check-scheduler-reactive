package com.empresa.financiera.infrastructure.service;

import com.empresa.financiera.domain.model.JobParameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Servicio mock que simula la obtención de parámetros desde una fuente externa
 * (Base de datos o API). Simula latencia de red/base de datos mediante delays reactivos.
 */
@Slf4j
@ApplicationScoped
public class MockParameterService {

    /**
     * Constructor explícito para mantener el patrón de inyección por constructor.
     * Aunque no tenga dependencias actualmente, define el patrón para futuras extensiones.
     */
    public MockParameterService() {
        // Constructor explícito para mantener el patrón de inyección por constructor
    }

    /**
     * Simula la obtención de parámetros de configuración desde una fuente externa.
     * Incluye simulación de latencia (100ms) usando operadores reactivos de Mutiny.
     * 
     * @return Uni que emite un JobParameters con los parámetros simulados
     */
    public Uni<JobParameters> fetchParameters() {
        log.info("Consultando fuente externa de parámetros...");
        
        return Uni.createFrom().item(() -> {
            // Valores simulados
            int randomSeconds = 10 + (int) (Math.random() * 21); // Entre 10 y 30 segundos incl.
            String interval = "PT" + randomSeconds + "S"; // Formato ISO-8601: PT{n}S
            String actionId = "ACTION_" + String.format("%03d", 1 + (int) (Math.random() * 1000));
            
            return new JobParameters(interval, actionId);
        })
        .onItem().delayIt().by(Duration.ofMillis(100))
        .onFailure().invoke(throwable -> 
            log.error("Error al consultar fuente externa de parámetros", throwable)
        );
    }
}

