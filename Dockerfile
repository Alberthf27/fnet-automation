# Etapa 1: Compilar con Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copiar archivos de configuración primero (para cacheo de dependencias)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -B

# Etapa 2: Imagen final ligera con JVM optimizado para Railway Free Tier (1GB RAM)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el JAR compilado desde la etapa anterior
COPY --from=builder /build/target/fnet-automation-1.0.0.jar app.jar

# Variables de entorno para el pool de conexiones
ENV DB_HOST=${DB_HOST}
ENV DB_PORT=${DB_PORT}
ENV DB_NAME=${DB_NAME}
ENV DB_USER=${DB_USER}
ENV DB_PASSWORD=${DB_PASSWORD}

# JVM optimizado para contenedor de 1GB:
# -XX:MaxRAMPercentage=75.0  →  Usa hasta 75% de la RAM del contenedor (~768MB de 1GB)
# -XX:+UseG1GC               →  G1 Garbage Collector (mejor para servers, pausas bajas)
# -XX:+ExitOnOutOfMemoryError →  Auto-restart en OOM (Railway lo reinicia)
# -XX:+HeapDumpOnOutOfMemoryError → Genera dump para diagnóstico
# -XshowSettings:vm           →  Muestra configuración de VM al iniciar
CMD ["java", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:MinRAMPercentage=50.0", \
     "-XX:+UseG1GC", \
     "-XX:MaxGCPauseMillis=200", \
     "-XX:+ExitOnOutOfMemoryError", \
     "-XX:+HeapDumpOnOutOfMemoryError", \
     "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
     "-XX:+PrintCommandLineFlags", \
     "-jar", "app.jar"]
