package servicio;

/**
 * Mock de Router para pruebas y desarrollo.
 * Simula cortes y reconexiones sin afectar equipos reales.
 * 
 * Cuando tengas las IPs de los routers MikroTik,
 * cambiarás a usar MikroTikRouterService.
 */
public class RouterServiceMock implements IRouterService {

    @Override
    public boolean cortarServicio(String ipCliente) {
        System.out.println("🔴 [MOCK] Simulando CORTE de servicio:");
        System.out.println("    📍 IP Cliente: " + ipCliente);
        System.out.println("    ✅ [MOCK] Corte simulado exitosamente");
        return true;
    }

    @Override
    public boolean reconectarServicio(String ipCliente) {
        System.out.println("🟢 [MOCK] Simulando RECONEXIÓN de servicio:");
        System.out.println("    📍 IP Cliente: " + ipCliente);
        System.out.println("    ✅ [MOCK] Reconexión simulada exitosamente");
        return true;
    }

    @Override
    public boolean verificarConexion() {
        System.out.println("🔧 [MOCK] Verificando conexión (simulada)");
        return true;
    }

    @Override
    public String getTipoRouter() {
        return "Router MOCK (Simulación)";
    }
}
