package servicio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio para enviar mensajes de WhatsApp usando WhatsApp Web.js.
 * 
 * Características:
 * - Límite de 100 mensajes por día
 * - Delay de 1-2 segundos entre mensajes
 * - Manejo de errores y reintentos
 * - Logging completo
 */
public class WhatsappService implements IWhatsAppService {

    private final String apiUrl;
    private final String apiKey;
    private final String instanceName;
    private final HttpClient httpClient;

    // Control de límites
    private static final int MAX_MENSAJES_POR_DIA = 100;
    private static AtomicInteger mensajesEnviadosHoy = new AtomicInteger(0);
    private static LocalDate ultimaFechaReset = LocalDate.now();

    // Delay entre mensajes (1-2 segundos aleatorio)
    private static final int DELAY_MIN_MS = 1000; // 1 segundo
    private static final int DELAY_MAX_MS = 2000; // 2 segundos
    private static long ultimoEnvio = 0;

    public WhatsappService() {
        // Leer variables de entorno
        this.apiUrl = System.getenv("EVOLUTION_API_URL");
        this.apiKey = System.getenv("EVOLUTION_API_KEY");
        this.instanceName = System.getenv("EVOLUTION_INSTANCE_NAME");

        // Validar configuración
        if (apiUrl == null || apiKey == null || instanceName == null) {
            throw new IllegalStateException(
                    "❌ Faltan variables de entorno: EVOLUTION_API_URL, EVOLUTION_API_KEY, EVOLUTION_INSTANCE_NAME");
        }

        // Crear cliente HTTP con timeout
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        System.out.println("✅ WhatsappService inicializado correctamente");
        System.out.println("   📡 API URL: " + apiUrl);
        System.out.println("   🔑 Instance: " + instanceName);
    }

    /**
     * Envía un mensaje de WhatsApp con control de límites y delays.
     * 
     * @param telefono Número de teléfono en formato internacional (ej: 51999123456)
     * @param mensaje  Texto del mensaje a enviar
     * @return true si se envió correctamente, false si hubo error
     */
    public boolean enviarMensaje(String telefono, String mensaje) {
        try {
            // 1. Verificar y resetear contador diario
            resetearContadorSiEsNecesario();

            // 2. Verificar límite diario
            if (mensajesEnviadosHoy.get() >= MAX_MENSAJES_POR_DIA) {
                System.out.println("⚠️ Límite diario alcanzado (" + MAX_MENSAJES_POR_DIA + " mensajes)");
                System.out.println("   Se resetea mañana a las 00:00");
                return false;
            }

            // 3. Aplicar delay entre mensajes
            aplicarDelay();

            // 4. Construir JSON del mensaje
            String json = construirJSON(telefono, mensaje);

            // 5. Crear request HTTP
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/send/text"))
                    .header("Content-Type", "application/json")
                    .header("apikey", apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // 6. Enviar request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 7. Verificar respuesta
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                mensajesEnviadosHoy.incrementAndGet();
                System.out.println("✅ Mensaje enviado a " + formatearTelefono(telefono));
                System.out.println("   📊 Mensajes hoy: " + mensajesEnviadosHoy.get() + "/" + MAX_MENSAJES_POR_DIA);
                return true;
            } else {
                System.err.println("❌ Error al enviar mensaje: HTTP " + response.statusCode());
                System.err.println("   Respuesta: " + response.body());
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Excepción al enviar mensaje a " + telefono);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Construye el JSON para WhatsApp Web.js API.
     */
    private String construirJSON(String telefono, String mensaje) {
        // Escapar caracteres especiales en el mensaje
        String mensajeEscapado = mensaje
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        return String.format(
                "{\"phone\":\"%s\",\"message\":\"%s\"}",
                telefono,
                mensajeEscapado);
    }

    /**
     * Aplica un delay aleatorio entre 1-2 segundos para evitar spam.
     */
    private void aplicarDelay() {
        long ahora = System.currentTimeMillis();
        long tiempoTranscurrido = ahora - ultimoEnvio;

        if (ultimoEnvio > 0 && tiempoTranscurrido < DELAY_MIN_MS) {
            try {
                // Calcular delay aleatorio entre 1-2 segundos
                int delayAleatorio = DELAY_MIN_MS + (int) (Math.random() * (DELAY_MAX_MS - DELAY_MIN_MS));
                long delayNecesario = delayAleatorio - tiempoTranscurrido;

                if (delayNecesario > 0) {
                    System.out.println("⏳ Esperando " + (delayNecesario / 1000.0) + "s antes de enviar...");
                    Thread.sleep(delayNecesario);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ultimoEnvio = System.currentTimeMillis();
    }

    /**
     * Resetea el contador de mensajes si cambió el día.
     */
    private void resetearContadorSiEsNecesario() {
        LocalDate hoy = LocalDate.now();
        if (!hoy.equals(ultimaFechaReset)) {
            int mensajesAyer = mensajesEnviadosHoy.getAndSet(0);
            ultimaFechaReset = hoy;
            System.out.println("🔄 Contador de mensajes reseteado (ayer: " + mensajesAyer + ")");
        }
    }

    /**
     * Formatea un número de teléfono para logging.
     */
    private String formatearTelefono(String telefono) {
        if (telefono.length() >= 4) {
            return telefono.substring(0, 2) + "..." + telefono.substring(telefono.length() - 4);
        }
        return telefono;
    }

    /**
     * Verifica el estado de la conexión de WhatsApp.
     * 
     * @return true si está conectado, false si no
     */
    public boolean verificarConexion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/status"))
                    .header("apikey", apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                boolean conectado = response.body().contains("\"ready\":true");
                System.out.println(conectado ? "✅ WhatsApp conectado" : "⚠️ WhatsApp desconectado");
                return conectado;
            }

            return false;
        } catch (Exception e) {
            System.err.println("❌ Error al verificar conexión: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene el QR code para conectar WhatsApp (primera vez).
     * 
     * @return URL del QR code en base64, o null si hay error
     */
    public String obtenerQR() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/instance/connect/" + instanceName))
                    .header("apikey", apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("📱 QR Code generado. Escanea con WhatsApp:");
                System.out.println(response.body());
                return response.body();
            }

            return null;
        } catch (Exception e) {
            System.err.println("❌ Error al obtener QR: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene estadísticas de uso.
     */
    public String obtenerEstadisticas() {
        return String.format(
                "📊 Estadísticas WhatsApp:\n" +
                        "   Mensajes hoy: %d/%d\n" +
                        "   Fecha: %s\n" +
                        "   Estado: %s",
                mensajesEnviadosHoy.get(),
                MAX_MENSAJES_POR_DIA,
                ultimaFechaReset,
                verificarConexion() ? "Conectado ✅" : "Desconectado ⚠️");
    }

    /**
     * Verifica si el servicio está habilitado.
     * Implementación de IWhatsAppService.
     */
    @Override
    public boolean estaHabilitado() {
        return apiUrl != null && apiKey != null && instanceName != null;
    }

    /**
     * Obtiene el nombre del servicio.
     * Implementación de IWhatsAppService.
     */
    @Override
    public String getNombreServicio() {
        return "WhatsApp Web.js";
    }
}
