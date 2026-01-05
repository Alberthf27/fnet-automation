package servicio;

import bd.Conexion;
import modelo.ConfiguracionSistema;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar la tabla configuracion_sistema.
 * Permite obtener y actualizar configuraciones globales del sistema.
 */
public class ConfiguracionDAO {

    // Claves predefinidas del sistema
    public static final String PLAZO_PAGO_DIAS = "plazo_pago_dias";
    public static final String DIAS_RECORDATORIO = "dias_recordatorio";
    public static final String WHATSAPP_HABILITADO = "whatsapp_habilitado";
    public static final String ROUTER_HABILITADO = "router_habilitado";
    public static final String CALLMEBOT_APIKEY = "callmebot_apikey";
    public static final String MIKROTIK_IP = "mikrotik_ip";
    public static final String MIKROTIK_USUARIO = "mikrotik_usuario";
    public static final String MIKROTIK_PASSWORD = "mikrotik_password";

    /**
     * Obtiene el valor de una configuración por su clave.
     * Retorna null si no existe.
     */
    public String obtenerValor(String clave) {
        String sql = "SELECT valor FROM configuracion_sistema WHERE clave = ?";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("valor");
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener configuración '" + clave + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Obtiene el valor como entero.
     * Retorna el valorPorDefecto si no existe o hay error.
     */
    public int obtenerValorInt(String clave, int valorPorDefecto) {
        String valor = obtenerValor(clave);
        if (valor == null)
            return valorPorDefecto;
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return valorPorDefecto;
        }
    }

    /**
     * Obtiene el valor como boolean (1/true = verdadero).
     */
    public boolean obtenerValorBoolean(String clave) {
        String valor = obtenerValor(clave);
        return "1".equals(valor) || "true".equalsIgnoreCase(valor);
    }

    /**
     * Actualiza o inserta una configuración (UPSERT).
     */
    public boolean guardarValor(String clave, String valor) {
        // Primero intentamos UPDATE
        String sqlUpdate = "UPDATE configuracion_sistema SET valor = ? WHERE clave = ?";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setString(1, valor);
            ps.setString(2, clave);
            int filas = ps.executeUpdate();

            if (filas == 0) {
                // No existía, hacemos INSERT
                String sqlInsert = "INSERT INTO configuracion_sistema (clave, valor) VALUES (?, ?)";
                try (PreparedStatement psInsert = conn.prepareStatement(sqlInsert)) {
                    psInsert.setString(1, clave);
                    psInsert.setString(2, valor);
                    return psInsert.executeUpdate() > 0;
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Error al guardar configuración '" + clave + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene todas las configuraciones del sistema.
     */
    public List<ConfiguracionSistema> obtenerTodas() {
        List<ConfiguracionSistema> lista = new ArrayList<>();
        String sql = "SELECT id_config, clave, valor, descripcion FROM configuracion_sistema ORDER BY clave";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ConfiguracionSistema c = new ConfiguracionSistema();
                c.setIdConfig(rs.getInt("id_config"));
                c.setClave(rs.getString("clave"));
                c.setValor(rs.getString("valor"));
                c.setDescripcion(rs.getString("descripcion"));
                lista.add(c);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Inicializa las configuraciones por defecto si no existen.
     * Debe llamarse al inicio de la aplicación.
     */
    public void inicializarConfiguraciones() {
        // Solo inserta si no existe (ignora si ya existe)
        String sql = "INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion) VALUES (?, ?, ?)";

        Object[][] defaults = {
                { PLAZO_PAGO_DIAS, "21", "Días de plazo tras vencimiento antes de corte (3 semanas)" },
                { DIAS_RECORDATORIO, "0", "Días después del vencimiento para enviar recordatorio (0 = mismo día)" },
                { WHATSAPP_HABILITADO, "0", "Activar envío de WhatsApp (1=sí, 0=no)" },
                { ROUTER_HABILITADO, "0", "Activar corte automático en router (1=sí, 0=no)" },
                { CALLMEBOT_APIKEY, "", "API Key de CallMeBot para WhatsApp" },
                { MIKROTIK_IP, "", "IP del router MikroTik principal" },
                { MIKROTIK_USUARIO, "admin", "Usuario de acceso al router MikroTik" },
                { MIKROTIK_PASSWORD, "", "Contraseña del router MikroTik" }
        };

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] config : defaults) {
                ps.setString(1, (String) config[0]);
                ps.setString(2, (String) config[1]);
                ps.setString(3, (String) config[2]);
                ps.addBatch();
            }
            ps.executeBatch();
            System.out.println("✅ Configuraciones del sistema inicializadas.");
        } catch (SQLException e) {
            // Ignoramos errores de duplicados
            if (!e.getMessage().contains("Duplicate")) {
                e.printStackTrace();
            }
        }
    }
}
