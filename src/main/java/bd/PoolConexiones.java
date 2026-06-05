package bd;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pool de conexiones HikariCP para el servicio de automatización.
 * Reemplaza la creación de conexiones individuales por un pool
 * gestionado que elimina el patrón "diente de sierra" de memoria.
 * 
 * Configuración optimizada para Railway Free Tier (1GB RAM):
 * - Máximo 10 conexiones en el pool (suficiente para un solo hilo)
 * - Timeout de conexión de 10s
 * - Tiempo máximo de vida de 30min (menor que el timeout de Railway)
 * - Leak detection de 60s (detecta conexiones no devueltas al pool)
 */
public class PoolConexiones {

    private static volatile HikariDataSource dataSource;
    private static final Object LOCK = new Object();

    private PoolConexiones() {
        // Singleton - no instanciar directamente
    }

    /**
     * Obtiene el DataSource compartido (inicialización lazy thread-safe).
     */
    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (LOCK) {
                if (dataSource == null) {
                    dataSource = crearPool();
                }
            }
        }
        return dataSource;
    }

    /**
     * Obtiene una conexión del pool.
     * Equivalente a Conexion.getConexion() pero desde el pool.
     */
    public static Connection getConexion() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Cierra el pool de conexiones (para shutdown graceful).
     */
    public static void cerrarPool() {
        synchronized (LOCK) {
            if (dataSource != null && !dataSource.isClosed()) {
                System.out.println("🛑 Cerrando pool de conexiones...");
                dataSource.close();
                dataSource = null;
                System.out.println("✅ Pool cerrado correctamente.");
            }
        }
    }

    /**
     * Verifica el estado del pool (útil para health checks).
     */
    public static String estadoPool() {
        if (dataSource == null) {
            return "Pool no inicializado";
        }
        var pool = dataSource.getHikariPoolMXBean();
        if (pool == null) {
            return "Pool MXBean no disponible";
        }
        return String.format(
                "Activas: %d | Inactivas: %d | Esperando: %d | Total: %d",
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getThreadsAwaitingConnection(),
                pool.getTotalConnections()
        );
    }

    /**
     * Crea y configura el pool HikariCP.
     */
    private static HikariDataSource crearPool() {
        String host = getEnvOrDefault("DB_HOST", "nozomi.proxy.rlwy.net");
        String port = getEnvOrDefault("DB_PORT", "20409");
        String db = getEnvOrDefault("DB_NAME", "railway");
        String user = getEnvOrDefault("DB_USER", "root");
        String password = getEnvOrDefault("DB_PASSWORD",
                "MUjBYtfwVPnAMAoHGDXbHqsIXYDTZnWs");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Parámetros de conexión MySQL
        config.addDataSourceProperty("useSSL", "false");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        config.addDataSourceProperty("serverTimezone", "America/Lima");
        config.addDataSourceProperty("useLegacyDatetimeCode", "false");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("connectTimeout", "10000");
        config.addDataSourceProperty("socketTimeout", "30000");

        // Configuración del pool optimizada para Railway Free Tier
        config.setMaximumPoolSize(10); // Máximo 10 conexiones concurrentes
        config.setMinimumIdle(2); // Mantener 2 conexiones inactivas listas
        config.setConnectionTimeout(10000); // 10s para obtener conexión
        config.setIdleTimeout(300000); // 5min inactiva → se cierra
        config.setMaxLifetime(1800000); // 30min vida máxima
        config.setLeakDetectionThreshold(60000); // Alerta si una conexión no se devuelve en 60s
        config.setPoolName("FnetAutomationPool");

        // Configuración para evitar errores de MySQL timeout en Railway proxy
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);

        System.out.println("✅ Pool de conexiones HikariCP inicializado");
        System.out.println("   🔗 " + host + ":" + port + "/" + db);
        System.out.println("   📊 Max: " + config.getMaximumPoolSize()
                + " | Min idle: " + config.getMinimumIdle());

        return new HikariDataSource(config);
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
