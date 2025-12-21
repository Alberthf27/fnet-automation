# Etapa 1: Compilar con Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copiar archivos de configuración primero (para cacheo de dependencias)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -B

# Etapa 2: Imagen final ligera
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el JAR compilado desde la etapa anterior
COPY --from=builder /build/target/fnet-automation-1.0.0.jar app.jar

# Comando de inicio
CMD ["java", "-jar", "app.jar"]
