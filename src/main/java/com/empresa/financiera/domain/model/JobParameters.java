package com.empresa.financiera.domain.model;

/**
 * DTO inmutable que representa los parámetros de configuración de un Job.
 * 
 * @param interval Intervalo en formato ISO-8601 duration (ej. "PT5S", "PT10S")
 * @param actionId Identificador de la acción a ejecutar
 */
public record JobParameters(
    String interval,
    String actionId
) {
}

