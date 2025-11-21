# MS POC State Check Scheduler Reactive

## Descripción

Este proyecto es una **Prueba de Concepto (PoC)** que implementa una **Estrategia de Verificación de Estado (Polling con Estado Compartido)** utilizando **Quarkus** y **Hibernate Reactive**. 

El objetivo es demostrar cómo dos jobs programados independientes pueden compartir estado de forma thread-safe y realizar operaciones de persistencia reactiva sin problemas de "Thread Drift".

## Objetivos del Proyecto

1. **Implementar Polling con Estado Compartido**: Dos jobs programados (`@Scheduled`) que comparten estado mediante `AtomicReference`.
2. **Validar Propagación de Contexto**: Asegurar que el contexto de persistencia reactiva se propaga correctamente entre los ciclos de ejecución.
3. **Resolver Thread Drift**: Implementar una solución robusta para evitar errores `HR000069` cuando los jobs se ejecutan en executor-threads.

## Arquitectura

### Patrón Implementado: Polling con Estado Compartido

El proyecto implementa un patrón donde:

- **Job 1 (Updater)**: Se ejecuta cada 10 segundos y actualiza los parámetros de configuración desde una fuente externa.
- **Job 2 (Worker)**: Se ejecuta cada 2 segundos y procesa los parámetros compartidos, persistiendo logs en la base de datos.

Ambos jobs comparten estado mediante `AtomicReference<JobParameters>`, garantizando thread-safety sin necesidad de bloqueos explícitos.

### Flujo de Ejecución

```
┌─────────────────────────────────────────────────────────────┐
│                    Scheduler (Quarkus)                      │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┴─────────────────┐
        │                                     │
        ▼                                     ▼
┌──────────────────┐              ┌──────────────────┐
│  Job 1 (Updater) │              │  Job 2 (Worker)  │
│  @Scheduled      │              │  @Scheduled      │
│  every="10s"     │              │  every="2s"      │
└──────────────────┘              └──────────────────┘
        │                                     │
        │ Obtiene parámetros                 │ Lee parámetros
        │ desde MockParameterService         │ desde AtomicReference
        │                                     │
        ▼                                     ▼
┌──────────────────┐              ┌──────────────────┐
│  AtomicReference │◄─────────────┤  BusinessTaskService│
│  <JobParameters> │              │  process()        │
│  (Thread-Safe)   │              │                    │
└──────────────────┘              └──────────────────┘
                                            │
                                            │ Panache.withTransaction()
                                            ▼
                                  ┌──────────────────┐
                                  │ ExecutionLog     │
                                  │ (Persistencia)   │
                                  └──────────────────┘
```

## Tecnologías Utilizadas

- **Java 21**: Lenguaje de programación
- **Quarkus 3.11.1**: Framework Java reactivo
- **Hibernate Reactive Panache**: Persistencia reactiva
- **PostgreSQL**: Base de datos (con Dev Services para desarrollo)
- **Mutiny**: Programación reactiva
- **Quarkus Scheduler**: Jobs programados
- **Lombok**: Utilidades (logging)
- **Awaitility**: Testing asíncrono
- **JUnit 5**: Framework de testing

## Estructura del Proyecto

```
src/
├── main/
│   ├── java/
│   │   └── com/empresa/financiera/
│   │       ├── application/
│   │       │   ├── StatefulJobService.java      # Servicio principal con jobs programados
│   │       │   └── service/
│   │       │       └── BusinessTaskService.java # Servicio de lógica de negocio
│   │       ├── domain/
│   │       │   └── model/
│   │       │       ├── JobParameters.java       # DTO de parámetros
│   │       │       └── ExecutionLog.java        # Entidad de persistencia
│   │       └── infrastructure/
│   │           ├── repository/
│   │           │   └── ExecutionLogRepository.java # Repositorio Panache Reactive
│   │           └── service/
│   │               └── MockParameterService.java    # Servicio mock de parámetros
│   └── resources/
│       └── application.properties                 # Configuración de la aplicación
└── test/
    ├── java/
    │   └── com/empresa/financiera/
    │       └── application/
    │           └── StatefulJobServiceTest.java   # Tests de integración
    └── resources/
        └── application.properties                # Configuración para tests
```

##  Configuración

### Requisitos Previos

- **Java 21** o superior
- **Maven 3.8+**
- **PostgreSQL** (opcional, se puede usar Dev Services)

### Configuración de Base de Datos

El proyecto está configurado para usar **PostgreSQL** con **Dev Services**. En desarrollo, Quarkus iniciará automáticamente un contenedor PostgreSQL.

Para producción, configura las siguientes propiedades en `application.properties`:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.reactive.url=vertx-reactive:postgresql://localhost:5432/tu_base_de_datos
quarkus.datasource.username=tu_usuario
quarkus.datasource.password=tu_contraseña
quarkus.hibernate-orm.database.generation=update
```

## Ejecución

### Modo Desarrollo

```bash
mvn quarkus:dev
```

Este comando:
- Inicia la aplicación en modo desarrollo
- Habilita hot-reload
- Inicia Dev Services (PostgreSQL automático)
- Los jobs programados se ejecutan automáticamente

### Compilación

```bash
mvn clean compile
```

### Ejecutar Tests

```bash
mvn test
```

### Construir JAR Ejecutable

```bash
mvn clean package
```

El JAR se generará en `target/quarkus-app/quarkus-run.jar`

## 🔍 Componentes Principales

### StatefulJobService

Servicio principal que implementa el patrón de Polling con Estado Compartido.

**Características**:
- Utiliza `AtomicReference<JobParameters>` para compartir estado de forma thread-safe
- Job 1 (`updateParameters`): Actualiza parámetros cada 10 segundos
- Job 2 (`executeWorkerLogic`): Procesa parámetros cada 2 segundos

**Ejemplo de uso**:
```java
@Scheduled(every = "10s")
Uni<Void> updateParameters() {
    return mockParameterService.fetchParameters()
        .invoke(params -> lastParams.set(params))
        .replaceWithVoid();
}

@Scheduled(every = "2s")
Uni<Void> executeWorkerLogic() {
    JobParameters params = lastParams.get();
    if (params == null) {
        return Uni.createFrom().voidItem();
    }
    return businessTaskService.process(params.actionId());
}
```

### BusinessTaskService

Servicio que encapsula la lógica de negocio y persistencia.

**Características**:
- Usa `Panache.withTransaction()` para manejar correctamente el contexto reactivo
- Evita problemas de "Thread Drift" al garantizar que toda la operación ocurra en el event loop thread correcto

**Implementación clave**:
```java
public Uni<Void> process(String params) {
    return Panache.withTransaction(() -> {
        ExecutionLog executionLog = new ExecutionLog();
        executionLog.paramsUsed = params;
        executionLog.timestamp = LocalDateTime.now();
        
        return executionLogRepository.persist(executionLog)
            .replaceWithVoid();
    });
}
```

## Solución al Problema de Thread Drift

### El Problema

Cuando los métodos `@Scheduled` se ejecutan, Quarkus los ejecuta en **executor-threads** (Worker threads). Sin embargo, Hibernate Reactive requiere que las operaciones de persistencia ocurran en **vert.x event loop threads**.

Si se usa `@WithTransaction` o `@WithSession`, el interceptor puede abrir la sesión en un hilo, pero el código puede intentar acceder a ella desde otro hilo, causando el error:

```
java.lang.IllegalStateException: HR000069: Detected use of the reactive Session API from the wrong Thread
```

### La Solución

**Usar `Panache.withTransaction()`** en lugar de anotaciones:

1. **Control Explícito del Contexto**: `Panache.withTransaction()` delimita explícitamente el alcance de la transacción y garantiza que toda la operación ocurra en el event loop thread correcto.

2. **Manejo Correcto del Cambio de Hilos**: Internamente, detecta que está en un executor-thread, cambia al event loop thread de Vert.x, abre la sesión, ejecuta el código y cierra la sesión, todo en el mismo hilo.

3. **Sin Dependencia de Interceptores**: No depende de interceptores CDI que pueden tener problemas con el cambio de hilos.

##  Testing

El proyecto incluye tests de integración que validan:

1. **Propagación de Estado**: Verifica que los parámetros cargados por el Job 1 son usados por el Job 2.
2. **Persistencia Reactiva**: Valida que los logs se persisten correctamente en la base de datos.
3. **Thread Safety**: Demuestra que el `AtomicReference` actúa correctamente como puente thread-safe.

### Ejecutar Tests

```bash
# Todos los tests
mvn test

# Test específico
mvn test -Dtest=StatefulJobServiceTest#testStatePropagation
```

##  Logs y Monitoreo

La aplicación genera logs estructurados que incluyen:

- Actualización de parámetros (cada 10 segundos)
- Ejecución de lógica de negocio (cada 2 segundos)
- Persistencia de logs
- Errores y excepciones

**Niveles de log**:
- `INFO`: Operaciones principales
- `DEBUG`: Detalles de ejecución (paquete `com.empresa.financiera`)

##  Seguridad y Mejores Prácticas

-  Inyección por constructor (mejor testabilidad)
-  Thread-safety mediante `AtomicReference`
-  Manejo correcto de contexto reactivo
-  Separación de responsabilidades (Domain, Application, Infrastructure)
-  Uso de Records para DTOs inmutables
-  Documentación JavaDoc completa

##  Notas Importantes

1. **Scheduler en Tests**: Los tests habilitan el scheduler para que los jobs se ejecuten automáticamente y tengan el contexto Vert.x correcto.

2. **Dev Services**: En desarrollo, Quarkus inicia automáticamente PostgreSQL. No es necesario tener PostgreSQL corriendo manualmente.

3. **Persistencia**: La estrategia de generación de esquema está configurada como `drop-and-create` para desarrollo. En producción, usar `update` o `none` con migraciones.

##  Contribución

Este es un proyecto de Prueba de Concepto. Para contribuir:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -am 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

##  Licencia

Este proyecto es una Prueba de Concepto interna.



