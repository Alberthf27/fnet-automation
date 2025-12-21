package servicio;

/**
 * Mock de WhatsApp para pruebas y desarrollo.
 * Simula el envío de mensajes sin hacer llamadas reales.
 * 
 * Cuando CallMeBot vuelva a estar disponible (después del 10 enero),
 * simplemente cambia la configuración para usar CallMeBotWhatsAppService.
 */
public class WhatsAppServiceMock implements IWhatsAppService {

    @Override
    public boolean enviarMensaje(String telefono, String mensaje) {
        System.out.println("📱 [MOCK] Simulando envío WhatsApp:");
        System.out.println("    📞 Teléfono: " + telefono);
        System.out.println("    💬 Mensaje: " + mensaje.substring(0, Math.min(50, mensaje.length())) + "...");
        System.out.println("    ✅ [MOCK] Mensaje simulado exitosamente");
        return true;
    }

    @Override
    public boolean estaHabilitado() {
        return true; // Siempre disponible en modo mock
    }

    @Override
    public String getNombreServicio() {
        return "WhatsApp MOCK (Simulación)";
    }
}
