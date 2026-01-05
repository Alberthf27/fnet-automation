package servicio;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Modelo para la cola de notificaciones de WhatsApp.
 * Almacena mensajes pendientes de envío a clientes.
 */
public class NotificacionPendiente {

    public enum TipoNotificacion {
        RECORDATORIO, ULTIMATUM, CORTE
    }

    public enum EstadoNotificacion {
        PENDIENTE, ENVIADO, ERROR, SIN_TELEFONO
    }

    private int idNotificacion;
    private int idSuscripcion;
    private TipoNotificacion tipo;
    private String mensaje;
    private String telefono;
    private Date fechaProgramada;
    private Timestamp fechaEnviado;
    private EstadoNotificacion estado;

    // Campos auxiliares para visualización
    private String nombreCliente;
    private String codigoContrato;

    public NotificacionPendiente() {
        this.estado = EstadoNotificacion.PENDIENTE;
    }

    // Getters y Setters
    public int getIdNotificacion() {
        return idNotificacion;
    }

    public void setIdNotificacion(int idNotificacion) {
        this.idNotificacion = idNotificacion;
    }

    public int getIdSuscripcion() {
        return idSuscripcion;
    }

    public void setIdSuscripcion(int idSuscripcion) {
        this.idSuscripcion = idSuscripcion;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public void setTipo(TipoNotificacion tipo) {
        this.tipo = tipo;
    }

    public void setTipo(String tipoStr) {
        try {
            this.tipo = TipoNotificacion.valueOf(tipoStr.toUpperCase());
        } catch (Exception e) {
            this.tipo = TipoNotificacion.RECORDATORIO;
        }
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public Date getFechaProgramada() {
        return fechaProgramada;
    }

    public void setFechaProgramada(Date fechaProgramada) {
        this.fechaProgramada = fechaProgramada;
    }

    public Timestamp getFechaEnviado() {
        return fechaEnviado;
    }

    public void setFechaEnviado(Timestamp fechaEnviado) {
        this.fechaEnviado = fechaEnviado;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoNotificacion estado) {
        this.estado = estado;
    }

    public void setEstado(String estadoStr) {
        try {
            this.estado = EstadoNotificacion.valueOf(estadoStr.toUpperCase());
        } catch (Exception e) {
            this.estado = EstadoNotificacion.PENDIENTE;
        }
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getCodigoContrato() {
        return codigoContrato;
    }

    public void setCodigoContrato(String codigoContrato) {
        this.codigoContrato = codigoContrato;
    }

    // Método de utilidad
    public boolean tieneTelefono() {
        return telefono != null && !telefono.trim().isEmpty();
    }
}
