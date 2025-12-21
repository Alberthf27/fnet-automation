package servicio;

import DAO.ConfiguracionDAO;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Motor principal de automatización para Railway.
 * Ejecuta el proceso de cobros cada hora.
 */
public class MotorAutomatizacion {

    private final CobrosAutomaticoService cobrosService;
    private final ConfiguracionDAO configDAO;
    private final ScheduledExecutorService scheduler;

    private boolean ejecutandose = false;

    public MotorAutomatizacion() {
        this.cobrosService = new CobrosAutomaticoService();
        this.configDAO = new ConfiguracionDAO();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Inicia el proceso automático.
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
        scheduler.scheduleAtFixedRate(this::procesarCiclo, 0, 1, TimeUnit.HOURS);
    }

    private void procesarCiclo() {
        try {
            System.out.println("\n⚙️ [Motor] Ejecutando ciclo de automatización...");

            boolean whatsappActivo = configDAO.obtenerValorBoolean(ConfiguracionDAO.WHATSAPP_HABILITADO);
            boolean routerActivo = configDAO.obtenerValorBoolean(ConfiguracionDAO.ROUTER_HABILITADO);

            if (!whatsappActivo && !routerActivo) {
                System.out.println("⏸️ Automatización desactivada.");
                return;
            }

            cobrosService.ejecutarProcesoDiario();

        } catch (Exception e) {
            System.err.println("❌ Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void ejecutarAhora() {
        System.out.println("🔄 Ejecutando proceso manualmente...");
        procesarCiclo();
    }

    public void detener() {
        if (scheduler != null && !scheduler.isShutdown()) {
            System.out.println("🛑 Deteniendo Motor...");
            scheduler.shutdown();
            ejecutandose = false;
        }
    }

    public boolean isEjecutandose() {
        return ejecutandose && !scheduler.isShutdown();
    }
}
