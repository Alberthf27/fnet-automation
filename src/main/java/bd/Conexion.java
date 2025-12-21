package bd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexión a base de datos para el servicio de automatización.
 * Usa variables de entorno para las credenciales (seguro para Railway).
 */
public class Conexion {

    private final String DRIVER = "com.mysql.cj.jdbc.Driver";

    // Credenciales desde variables de entorno (Railway las configura)
    private final String HOST = getEnvOrDefault("DB_HOST", "nozomi.proxy.rlwy.net");
    private final String PORT = getEnvOrDefault("DB_PORT", "20409");
    private final String DB = getEnvOrDefault("DB_NAME", "railway");
    private final String USER = getEnvOrDefault("DB_USER", "root");
    private final String PASSWORD = getEnvOrDefault("DB_PASSWORD", "MUjBYtfwVPnAMAoHGDXbHqsIXYDTZnWs");

    private final String URL;
    public Connection cadena;

    public Conexion() {
        this.URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=America/Lima"
                + "&useLegacyDatetimeCode=false"
                + "&autoReconnect=true"
                + "&characterEncoding=UTF-8"
                + "&useUnicode=true"
                + "&connectTimeout=15000";
        this.cadena = null;
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public Connection conectar() {
        int intentos = 0;
        int maxIntentos = 5;

        while (intentos < maxIntentos) {
            try {
                Class.forName(DRIVER);
                this.cadena = DriverManager.getConnection(URL, USER, PASSWORD);
                return this.cadena;
            } catch (ClassNotFoundException | SQLException e) {
                intentos++;
                System.out.println("⚠ Intento " + intentos + "/" + maxIntentos + " fallido: " + e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.err.println("❌ Error crítico: No se pudo conectar a la BD después de " + maxIntentos + " intentos");
        return null;
    }

    public void desconectar() {
        try {
            if (this.cadena != null && !this.cadena.isClosed()) {
                this.cadena.close();
            }
        } catch (SQLException e) {
            // Ignorar errores al cerrar
        }
    }

    public static Connection getConexion() {
        return new Conexion().conectar();
    }
}
