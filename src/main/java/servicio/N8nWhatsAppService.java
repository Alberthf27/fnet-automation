package servicio;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Servicio de WhatsApp que llama al webhook de n8n.
 * n8n se encarga de reenviar a CallMeBot.
 */
public class N8nWhatsAppService implements IWhatsAppService {

    // URL del webhook de n8n en Railway
    private static final String WEBHOOK_URL = System.getenv("N8N_WEBHOOK_URL") != null
            ? System.getenv("N8N_WEBHOOK_URL")
            : "https://n8n-production-6de3.up.railway.app/webhook/enviar-whatsapp";

    @Override
    public boolean enviarMensaje(String telefono, String mensaje) {
        if (telefono == null || telefono.isEmpty()) {
            System.err.println("❌ Teléfono vacío");
            return false;
        }

        try {
            // Construir URL con query params
            String urlString = WEBHOOK_URL
                    + "?telefono=" + URLEncoder.encode(telefono, StandardCharsets.UTF_8)
                    + "&mensaje=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("✅ Mensaje enviado a n8n para: " + telefono);
                return true;
            } else {
                System.err.println("⚠️ n8n respondió con código: " + responseCode);
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Error enviando a n8n: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean estaHabilitado() {
        // Verificar si el servicio está configurado
        return WEBHOOK_URL != null && !WEBHOOK_URL.isEmpty();
    }

    @Override
    public String getNombreServicio() {
        return "n8n-WhatsApp";
    }
}
