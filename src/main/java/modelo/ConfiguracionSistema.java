package modelo;

/**
 * Modelo para configuraciones globales del sistema.
 * Almacena parámetros como plazo de pago, flags de activación, etc.
 */
public class ConfiguracionSistema {

    private int idConfig;
    private String clave;
    private String valor;
    private String descripcion;

    public ConfiguracionSistema() {
    }

    public ConfiguracionSistema(String clave, String valor) {
        this.clave = clave;
        this.valor = valor;
    }

    // Getters y Setters
    public int getIdConfig() {
        return idConfig;
    }

    public void setIdConfig(int idConfig) {
        this.idConfig = idConfig;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    // Métodos de utilidad para conversión
    public int getValorInt() {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean getValorBoolean() {
        return "1".equals(valor) || "true".equalsIgnoreCase(valor);
    }

    public double getValorDouble() {
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
