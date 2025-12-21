package servicio;

/**
 * Interface para servicios de WhatsApp.
 */
public interface IWhatsAppService {
    boolean enviarMensaje(String telefono, String mensaje);

    boolean estaHabilitado();

    String getNombreServicio();
}
