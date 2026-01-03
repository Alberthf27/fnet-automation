package servicio;

import DAO.ConfiguracionDAO;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Motor principal de automatización que ejecuta el proceso de cobros.
 * 
 * Llámalo UNA VEZ al iniciar la aplicación:
 * new MotorAutomatizacion().iniciarServicio();
 * 
 * Este motor ejecuta cada hora:
 * 1. Generación de facturas (si es día 1 del mes)
 * 2. Revisión de facturas vencidas → programa recordatorios
 * 3. Revisión de ultimátums vencidos → ejecuta cortes
 * 4. Procesamiento de cola de notificaciones WhatsApp
 */
public class MotorAutomatizacion {

    private final CobrosAutomaticoService cobrosService;
    private final ConfiguracionDAO configDAO;
    private final ScheduledExecutorService scheduler;
    private final EmailMonitorService emailMonitor;
    private final YapePagoProcessor yapeProcesador;

    private boolean ejecutandose = false;

    public MotorAutomatizacion() {
        this.cobrosService = new CobrosAutomaticoService();
        this.configDAO = new ConfiguracionDAO();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.emailMonitor = new EmailMonitorService();
        this.yapeProcesador = new YapePagoProcessor();
    }

    /**
     * Inicia el proceso automático.
     * Llámalo UNA VEZ al arrancar el sistema (en Principal o Login).
     */
    public void iniciarServicio() {
        if (ejecutandose) {
            System.out.println("⚠️ Motor de Automatización ya está corriendo.");
            return;
        }

        ejecutandose = true;
        System.out.println("🚀 Motor de Automatización Iniciado...");
        System.out.println("   ⏰ Ejecutará proceso de cobros cada hora.");

        // Ejecutar cada 1 HORA
        // Para pruebas: cambiar TimeUnit.HOURS a TimeUnit.MINUTES
        scheduler.scheduleAtFixedRate(this::procesarCiclo, 0, 1, TimeUnit.HOURS);
    }

    /**
     * Ejecuta el ciclo de procesamiento.
     * SIEMPRE genera facturas. Solo las notificaciones y cortes dependen de la
     * configuración.
     */
    private void procesarCiclo() {
        try {
            System.out.println("\n⚙️ [Motor] Ejecutando ciclo de automatización...");

            // SIEMPRE ejecutar el proceso de cobros
            // La generación de facturas es independiente de WhatsApp/Router
            cobrosService.ejecutarProcesoDiario();

            // Procesar pagos Yape - DESHABILITADO (usar PanelSubirYape manual)
            /*
             * try {
             * System.out.println("\n💰 Procesando pagos Yape...");
             * java.util.List<java.io.File> excels = emailMonitor.descargarNuevosReportes();
             * 
             * for (java.io.File excel : excels) {
             * yapeProcesador.procesarExcel(excel);
             * }
             * 
             * // Limpiar archivos procesados
             * emailMonitor.limpiarArchivosAntiguos();
             * 
             * } catch (Exception e) {
             * System.err.println("❌ Error procesando pagos Yape: " + e.getMessage());
             * e.printStackTrace();
             * }
             */

        } catch (Exception e) {
            System.err.println("❌ Error crítico en MotorAutomatizacion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ejecuta el proceso de cobros manualmente (sin esperar al scheduler).
     * Útil para testing o ejecución manual desde la UI.
     */
    public void ejecutarAhora() {
        System.out.println("🔄 Ejecutando proceso de cobros manualmente...");
        procesarCiclo();
    }

    /**
     * Detiene el motor de automatización de forma segura.
     */
    public void detener() {
        if (scheduler != null && !scheduler.isShutdown()) {
            System.out.println("🛑 Deteniendo Motor de Automatización...");
            scheduler.shutdown();
            ejecutandose = false;
        }
    }

    /**
     * Verifica si el motor está ejecutándose.
     */
    public boolean isEjecutandose() {
        return ejecutandose && !scheduler.isShutdown();
    }
}