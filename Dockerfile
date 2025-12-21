FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el JAR compilado
COPY target/fnet-automation-1.0.0.jar app.jar

# Exponer puerto (opcional, para health checks)
EXPOSE 8080

# Comando de inicio
CMD ["java", "-jar", "app.jar"]
