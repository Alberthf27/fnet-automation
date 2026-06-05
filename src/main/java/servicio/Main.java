package servicio;

/**
 * FNET Automation Service
 * Servicio independiente para ejecutar automatizaciones de cobros
 * Diseñado para correr en Railway 24/7
 */
public class Main {

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Lima"));
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🚀 FNET AUTOMATION SERVICE - Iniciando...");
        System.out.println("═══════════════════════════════════════════════════════════");

        // Inicializar pool de conexiones (HikariCP) al arrancar
        try {
            bd.PoolConexiones.getDataSource();
            System.out.println("✅ Pool de conexiones inicializado");
            System.out.println("   📊 " + bd.PoolConexiones.estadoPool());
        } catch (Exception e) {
            System.err.println("❌ Error crítico inicializando pool: " + e.getMessage());
            System.exit(1);
        }

        // Verificar conexión a base de datos usando el pool
        try (java.sql.Connection conn = bd.Conexion.getConexion()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("✅ Conexión a base de datos exitosa (desde pool)");
            } else {
                System.err.println("❌ Error conectando a la base de datos");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("❌ Error crítico de conexión: " + e.getMessage());
            System.exit(1);
        }

        // Registrar shutdown hook para cerrar el pool gracefulmente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutdown hook activado. Cerrando recursos...");
            bd.PoolConexiones.cerrarPool();
            System.out.println("✅ Recursos liberados. Hasta luego.");
        }));

        // Iniciar el motor de automatización
        servicio.MotorAutomatizacion motor = new servicio.MotorAutomatizacion();
        motor.iniciarServicio();

        System.out.println("✅ Motor de automatización iniciado");
        System.out.println("📅 Ejecutando tareas cada hora...");
        System.out.println("   📊 Pool: " + bd.PoolConexiones.estadoPool());

        // Mantener el proceso vivo y monitorear el pool periódicamente
        try {
            int ciclos = 0;
            while (true) {
                Thread.sleep(60000); // Dormir 1 minuto
                ciclos++;

                // Cada 30 minutos, reportar estado del pool
                if (ciclos % 30 == 0) {
                    System.out.println("📊 [Monitor] Estado del pool: " + bd.PoolConexiones.estadoPool());
                }
            }
        } catch (InterruptedException e) {
            System.out.println("⚠️ Servicio interrumpido");
            Thread.currentThread().interrupt();
        }
    }
}
