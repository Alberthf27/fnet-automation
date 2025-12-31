package servicio;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/**
 * Implementación de WhatsApp usando Twilio API.
 * Reemplaza a CallMeBot/n8n para mayor confiabilidad.
 */
public class TwilioWhatsAppService implements IWhatsAppService {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber; // Formato: whatsapp:+14155238886

    public TwilioWhatsAppService() {
        // Leer credenciales desde variables de entorno
        this.accountSid = System.getenv("TWILIO_ACCOUNT_SID");
        this.authToken = System.getenv("TWILIO_AUTH_TOKEN");
        this.fromNumber = System.getenv("TWILIO_WHATSAPP_FROM");

        // Inicializar Twilio
        if (estaHabilitado()) {
            Twilio.init(accountSid, authToken);
            System.out.println("✅ Twilio WhatsApp Service inicializado correctamente");
        } else {
            System.err.println("⚠️ Twilio no configurado. Verifica las variables de entorno:");
            System.err.println("   - TWILIO_ACCOUNT_SID");
            System.err.println("   - TWILIO_AUTH_TOKEN");
            System.err.println("   - TWILIO_WHATSAPP_FROM");
        }
    }

    @Override
    public boolean enviarMensaje(String telefono, String mensaje) {
        if (!estaHabilitado()) {
            System.err.println("❌ Twilio no está habilitado. No se puede enviar mensaje.");
            return false;
        }

        if (telefono == null || telefono.isEmpty()) {
            System.err.println("❌ Número de teléfono vacío");
            return false;
        }

        try {
            // Formatear número de destino
            // Si el número no tiene el prefijo whatsapp:, agregarlo
            String toNumber = telefono.startsWith("whatsapp:")
                    ? telefono
                    : "whatsapp:+51" + telefono.replaceAll("[^0-9]", "");

            // Enviar mensaje vía Twilio
            Message twilioMessage = Message.creator(
                    new PhoneNumber(toNumber), // To
                    new PhoneNumber(fromNumber), // From (número de Twilio)
                    mensaje // Body
            ).create();

            String sid = twilioMessage.getSid();
            String status = twilioMessage.getStatus().toString();

            System.out.println("✅ Mensaje enviado vía Twilio");
            System.out.println("   📱 Destino: " + toNumber);
            System.out.println("   🆔 SID: " + sid);
            System.out.println("   📊 Estado: " + status);

            return true;

        } catch (Exception e) {
            System.err.println("❌ Error enviando mensaje vía Twilio: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean estaHabilitado() {
        return accountSid != null && !accountSid.isEmpty()
                && authToken != null && !authToken.isEmpty()
                && fromNumber != null && !fromNumber.isEmpty();
    }

    @Override
    public String getNombreServicio() {
        return "Twilio-WhatsApp";
    }

    /**
     * Método de prueba para verificar la configuración.
     */
    public void verificarConfiguracion() {
        System.out.println("🔍 Verificando configuración de Twilio:");
        System.out.println("   Account SID: " + (accountSid != null ? "✅ Configurado" : "❌ No configurado"));
        System.out.println("   Auth Token: " + (authToken != null ? "✅ Configurado" : "❌ No configurado"));
        System.out.println("   From Number: " + (fromNumber != null ? fromNumber : "❌ No configurado"));
        System.out.println("   Estado: " + (estaHabilitado() ? "✅ Listo para usar" : "❌ Falta configuración"));
    }
}
