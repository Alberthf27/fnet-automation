# FNET Automation Service

Servicio de automatización de cobros para ejecutar en Railway 24/7.

## Archivos Necesarios

Debes copiar estos archivos desde tu proyecto principal `fnet/src/`:

### Servicios (copiar a `src/main/java/servicio/`)
- [x] MotorAutomatizacion.java (ya creado/adaptado)
- [x] N8nWhatsAppService.java (nuevo, ya creado)
- [ ] CobrosAutomaticoService.java (copiar y modificar)
- [ ] MensajeTemplateService.java (copiar)
- [ ] MikroTikRouterService.java (copiar)

### DAOs (copiar a `src/main/java/DAO/`)
- [ ] ConfiguracionDAO.java
- [ ] NotificacionDAO.java
- [ ] AlertaDAO.java
- [ ] PagoDAO.java
- [ ] SuscripcionDAO.java

### Modelos (copiar a `src/main/java/modelo/`)
- [ ] NotificacionPendiente.java
- [ ] AlertaGerente.java
- [ ] ConfiguracionSistema.java

## Variables de Entorno en Railway

Configura estas variables en Railway Settings → Variables:

```
DB_HOST=nozomi.proxy.rlwy.net
DB_PORT=20409
DB_NAME=railway
DB_USER=root
DB_PASSWORD=MUjBYtfwVPnAMAoHGDXbHqsIXYDTZnWs
N8N_WEBHOOK_URL=https://n8n-production-6de3.up.railway.app/webhook/enviar-whatsapp
```

## Desplegar en Railway

1. Subir proyecto a GitHub
2. En Railway: New Project → Deploy from GitHub repo
3. Railway detectará el `pom.xml` o `Dockerfile`
4. Configurar variables de entorno
5. ¡Listo!

## Comandos Locales

```bash
# Compilar
mvn clean package

# Ejecutar localmente
java -jar target/fnet-automation-1.0.0.jar
```
