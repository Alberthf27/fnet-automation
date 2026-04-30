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

        // Verificar conexión a base de datos
        try {
            java.sql.Connection conn = bd.Conexion.getConexion();
            if (conn != null) {
                System.out.println("✅ Conexión a base de datos exitosa");
                conn.close();
            } else {
                System.err.println("❌ Error conectando a la base de datos");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("❌ Error crítico de conexión: " + e.getMessage());
            System.exit(1);
        }

        // Iniciar el motor de automatización
        servicio.MotorAutomatizacion motor = new servicio.MotorAutomatizacion();
        motor.iniciarServicio();

        System.out.println("✅ Motor de automatización iniciado");
        System.out.println("📅 Ejecutando tareas cada hora...");

        // Mantener el proceso vivo
        try {
            while (true) {
                Thread.sleep(60000); // Dormir 1 minuto
            }
        } catch (InterruptedException e) {
            System.out.println("⚠️ Servicio interrumpido");
        }
    }
}

