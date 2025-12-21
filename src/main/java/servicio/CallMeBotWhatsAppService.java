package servicio;

import DAO.ConfiguracionDAO;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Implementación de WhatsApp usando CallMeBot API.
 * 
 * ESTADO: CallMeBot suspendido temporalmente hasta ~10 de enero 2025.
 * Una vez disponible, configura la API key en configuracion_sistema.
 * 
 * Para activar:
 * 1. Obtener API key en
 * https://www.callmebot.com/blog/free-api-whatsapp-messages/
 * 2. Guardar en BD: INSERT INTO configuracion_sistema (clave, valor) VALUES
 * ('callmebot_apikey', 'TU_API_KEY');
 * 3. Activar en BD: UPDATE configuracion_sistema SET valor = '1' WHERE clave =
 * 'whatsapp_habilitado';
 */
public class CallMeBotWhatsAppService implements IWhatsAppService {

    private final ConfiguracionDAO configDAO;
    private String apiKey;

    public CallMeBotWhatsAppService() {
        this.configDAO = new ConfiguracionDAO();
        this.apiKey = configDAO.obtenerValor(ConfiguracionDAO.CALLMEBOT_APIKEY);
    }

    @Override
    public boolean enviarMensaje(String telefono, String mensaje) {
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("❌ CallMeBot: API Key no configurada");
            return false;
        }

        if (telefono == null || telefono.isEmpty()) {
            System.err.println("❌ CallMeBot: Teléfono vacío");
            return false;
        }

        try {
            // Limpiar número de teléfono (solo dígitos, agregar código país si falta)
            String telefonoLimpio = limpiarTelefono(telefono);

            // Codificar mensaje para URL
            String mensajeCodificado = URLEncoder.encode(mensaje, StandardCharsets.UTF_8.toString());

            // Construir URL de CallMeBot
            String urlStr = String.format(
                    "https://api.callmebot.com/whatsapp.php?phone=%s&text=%s&apikey=%s",
                    telefonoLimpio,
                    mensajeCodificado,
                    apiKey);

            // Hacer petición HTTP
            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);

            int responseCode = con.getResponseCode();

            if (responseCode == 200) {
                // Leer respuesta
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String response = in.readLine();
                in.close();

                System.out.println("📱 CallMeBot: Mensaje enviado a " + telefonoLimpio);
                return true;
            } else {
                System.err.println("❌ CallMeBot: Error HTTP " + responseCode);
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ CallMeBot: Error enviando mensaje - " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean estaHabilitado() {
        return configDAO.obtenerValorBoolean(ConfiguracionDAO.WHATSAPP_HABILITADO)
                && apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getNombreServicio() {
        return "CallMeBot WhatsApp API";
    }

    /**
     * Limpia y formatea el número de teléfono.
     * Agrega código de país Perú (+51) si no lo tiene.
     */
    private String limpiarTelefono(String telefono) {
        // Remover espacios, guiones, paréntesis
        String limpio = telefono.replaceAll("[^0-9+]", "");

        // Si empieza con 9 y tiene 9 dígitos, agregar +51 (Perú)
        if (limpio.startsWith("9") && limpio.length() == 9) {
            limpio = "51" + limpio;
        }

        // Si no empieza con +, asumimos que es peruano
        if (!limpio.startsWith("+") && !limpio.startsWith("51")) {
            limpio = "51" + limpio;
        }

        // Remover el + si existe (CallMeBot lo maneja sin él)
        limpio = limpio.replace("+", "");

        return limpio;
    }
}
