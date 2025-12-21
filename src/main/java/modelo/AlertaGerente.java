package modelo;

import java.sql.Timestamp;

/**
 * Modelo para alertas dirigidas al gerente.
 * Se muestran en la bandeja de entrada cuando hay situaciones que requieren
 * atención manual.
 */
public class AlertaGerente {

    public enum TipoAlerta {
        SIN_TELEFONO, // Cliente sin número de WhatsApp
        PAGO_MANUAL, // Pago requiere verificación manual
        CORTE_FALLIDO, // No se pudo cortar el servicio en el router
        RECONEXION_FALLO, // No se pudo reconectar el servicio
        OTRO // Otros tipos de alerta
    }

    private int idAlerta;
    private TipoAlerta tipo;
    private String titulo;
    private String mensaje;
    private Integer idSuscripcion; // Puede ser null
    private boolean leido;
    private Timestamp fechaCreacion;

    // Campos auxiliares para visualización
    private String nombreCliente;
    private String codigoContrato;

    public AlertaGerente() {
        this.leido = false;
        this.fechaCreacion = new Timestamp(System.currentTimeMillis());
    }

    public AlertaGerente(TipoAlerta tipo, String titulo, String mensaje) {
        this();
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
    }

    // Getters y Setters
    public int getIdAlerta() {
        return idAlerta;
    }

    public void setIdAlerta(int idAlerta) {
        this.idAlerta = idAlerta;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public void setTipo(TipoAlerta tipo) {
        this.tipo = tipo;
    }

    public void setTipo(String tipoStr) {
        try {
            this.tipo = TipoAlerta.valueOf(tipoStr.toUpperCase());
        } catch (Exception e) {
            this.tipo = TipoAlerta.OTRO;
        }
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Integer getIdSuscripcion() {
        return idSuscripcion;
    }

    public void setIdSuscripcion(Integer idSuscripcion) {
        this.idSuscripcion = idSuscripcion;
    }

    public boolean isLeido() {
        return leido;
    }

    public void setLeido(boolean leido) {
        this.leido = leido;
    }

    public Timestamp getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Timestamp fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
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

    // Método de utilidad para icono
    public String getIcono() {
        switch (tipo) {
            case SIN_TELEFONO:
                return "📵";
            case PAGO_MANUAL:
                return "💵";
            case CORTE_FALLIDO:
                return "❌";
            case RECONEXION_FALLO:
                return "🔌";
            default:
                return "⚠️";
        }
    }
}
