package servicio;

import DAO.*;
import modelo.*;
import modelo.NotificacionPendiente.TipoNotificacion;
import modelo.NotificacionPendiente.EstadoNotificacion;
import bd.Conexion;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Servicio principal que orquesta todo el proceso de cobros automáticos.
 * 
 * Este servicio debe ejecutarse diariamente (manualmente o por scheduler).
 * Coordina: generación de facturas, envío de notificaciones, cortes y
 * reconexiones.
 */
public class CobrosAutomaticoService {

    private final ConfiguracionDAO configDAO;
    private final NotificacionDAO notificacionDAO;
    private final AlertaDAO alertaDAO;
    private final SuscripcionDAO suscripcionDAO;
    private final PagoDAO pagoDAO;

    private final IWhatsAppService whatsAppService;
    private final IRouterService routerService;
    private final MensajeTemplateService mensajeService;

    public CobrosAutomaticoService() {
        this.configDAO = new ConfiguracionDAO();
        this.notificacionDAO = new NotificacionDAO();
        this.alertaDAO = new AlertaDAO();
        this.suscripcionDAO = new SuscripcionDAO();
        this.pagoDAO = new PagoDAO();
        this.mensajeService = new MensajeTemplateService();

        // WhatsApp con Evolution API - ACTIVADO INMEDIATAMENTE
        if (configDAO.obtenerValorBoolean(ConfiguracionDAO.WHATSAPP_HABILITADO)) {
            this.whatsAppService = new WhatsappService(); // Evolution API (gratis, sin límites)
            System.out.println("📱 WhatsApp REAL activado (Evolution API)");
        } else {
            this.whatsAppService = new WhatsAppServiceMock();
            System.out.println("📱 WhatsApp DESHABILITADO en configuración");
        }

        if (configDAO.obtenerValorBoolean(ConfiguracionDAO.ROUTER_HABILITADO)) {
            this.routerService = new MikroTikRouterService();
        } else {
            this.routerService = new RouterServiceMock();
        }
    }

    /**
     * PROCESO DIARIO PRINCIPAL.
     * Debe ejecutarse una vez al día (preferiblemente en la mañana).
     */
    public void ejecutarProcesoDiario() {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("🔄 INICIANDO PROCESO DIARIO DE COBROS - " + LocalDate.now());
        System.out.println("═══════════════════════════════════════════════════════════");

        // 1. Generar facturas - SOLO 2 veces al día (8AM y 6PM) o al iniciar
        int horaActual = java.time.LocalTime.now().getHour();
        if (horaActual == 8 || horaActual == 18 || primeraEjecucionDelDia()) {
            System.out.println("\n📋 [Generación de Facturas - " + horaActual + ":00]");
            generarFacturasFaltantes();
        } else {
            System.out.println("\n📋 Generación de facturas: Solo a las 8AM y 6PM (actual: " + horaActual + ":00)");
        }

        // 2. Revisar facturas vencidas y crear notificaciones de recordatorio (CADA
        // HORA)
        revisarFacturasVencidas();

        // 3. Revisar ultimátums vencidos y ejecutar cortes (CADA HORA)
        revisarUltimatumsVencidos();

        // 4. Procesar cola de notificaciones pendientes (CADA HORA)
        procesarNotificacionesPendientes();

        // 5. Limpiar alertas antiguas
        int eliminadas = alertaDAO.limpiarAlertas();
        if (eliminadas > 0) {
            System.out.println("🧹 Eliminadas " + eliminadas + " alertas antiguas.");
        }

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("✅ PROCESO DIARIO COMPLETADO");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    // Controla si es la primera ejecución del día
    private static java.time.LocalDate ultimaFechaEjecucion = null;

    private boolean primeraEjecucionDelDia() {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        if (ultimaFechaEjecucion == null || !ultimaFechaEjecucion.equals(hoy)) {
            ultimaFechaEjecucion = hoy;
            return true;
        }
        return false;
    }

    /**
     * NUEVO: Genera facturas faltantes para TODOS los clientes activos.
     * No depende del día de pago - revisa cada suscripción y genera
     * la factura del siguiente periodo si corresponde.
     */
    public void generarFacturasFaltantes() {
        System.out.println("\n📋 Revisando facturas faltantes...");

        // Seleccionar TODAS las suscripciones activas
        String sql = "SELECT s.id_suscripcion, s.id_cliente, s.mes_adelantado, s.dia_pago, " +
                "c.nombres, c.apellidos, c.telefono, c.dni_cliente, " + // CORREGIDO: c.dni_cliente
                "srv.mensualidad, s.codigo_contrato " +
                "FROM suscripcion s " +
                "JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "JOIN servicio srv ON s.id_servicio = srv.id_servicio " +
                "WHERE s.activo = 1";

        int facturasGeneradas = 0;
        int clientesRevisados = 0;
        int notificacionesProgramadas = 0;

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clientesRevisados++;
                    int idSuscripcion = rs.getInt("id_suscripcion");
                    boolean mesAdelantado = rs.getInt("mes_adelantado") == 1;
                    String nombreCliente = rs.getString("nombres") + " " + rs.getString("apellidos");
                    String telefono = rs.getString("telefono");
                    String dni = rs.getString("dni_cliente"); // CORREGIDO: dni_cliente
                    double monto = rs.getDouble("mensualidad");

                    // Intentar generar factura (el método ya valida si corresponde)
                    boolean generada = pagoDAO.generarSiguienteFactura(idSuscripcion);

                    if (generada) {
                        facturasGeneradas++;

                        // Contar facturas pendientes DESPUÉS de generar la nueva
                        int facturasPendientes = pagoDAO.contarFacturasPendientes(idSuscripcion);

                        // ⚠️ MODO PRUEBA: Solo enviar a DNI 44085317
                        boolean esPrueba = "44085317".equals(dni);

                        if (esPrueba) {
                            // Obtener información de la factura recién generada
                            String facturaInfo = pagoDAO.obtenerUltimaFacturaInfo(idSuscripcion);

                            // Obtener lista de facturas pendientes
                            String facturasPendientesDetalle = pagoDAO.obtenerFacturasPendientesDetalle(idSuscripcion);

                            // Construir mensaje mejorado
                            StringBuilder mensaje = new StringBuilder();
                            mensaje.append("Hola ").append(nombreCliente).append(", ");
                            mensaje.append("te recordamos que ya está disponible tu pago ").append(facturaInfo);
                            mensaje.append(" por S/. ").append(String.format("%.2f", monto)).append(".");

                            // Agregar facturas pendientes si hay más de una
                            if (facturasPendientes > 1) {
                                mensaje.append("\n\n📋 Facturas pendientes:\n").append(facturasPendientesDetalle);
                            }

                            mensaje.append("\n\n¡Gracias!");

                            whatsAppService.enviarMensaje(telefono, mensaje.toString());
                            notificacionesProgramadas++;
                            System.out.println("   📱 Notificación enviada a cliente de prueba: " + nombreCliente);
                        } else {
                            System.out.println(
                                    "   ⏭️ Cliente omitido (no es prueba): " + nombreCliente + " - DNI: " + dni);
                        }

                        // ADVERTENCIA DE CORTE: Si llega a 3 meses de deuda (solo para cliente de
                        // prueba)
                        if (esPrueba && facturasPendientes >= 3) {
                            double deudaTotal = monto * facturasPendientes;
                            String mensajeUrgente = String.format(
                                    "⚠️ AVISO IMPORTANTE %s: Tienes %d meses de deuda acumulada (S/. %.2f). " +
                                            "Debes regularizar tu pago en los próximos 5 días para evitar el corte de servicio. "
                                            +
                                            "Comunícate con nosotros para coordinar tu pago.",
                                    nombreCliente, facturasPendientes, deudaTotal);
                            whatsAppService.enviarMensaje(telefono, mensajeUrgente);
                            System.out.println("   ⚠️ Advertencia de corte enviada a: " + nombreCliente);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("   📊 Clientes revisados: " + clientesRevisados);
        System.out.println("   ✅ Facturas generadas: " + facturasGeneradas);
        if (notificacionesProgramadas > 0) {
            System.out.println("   📱 Notificaciones programadas: " + notificacionesProgramadas);
        }
    }

    /**
     * @deprecated Usar generarFacturasFaltantes() en su lugar.
     *             Genera las facturas para clientes cuyo día de pago es el
     *             especificado.
     */
    @Deprecated
    public void generarFacturasDelDia(int diaPago) {
        // Mantener por compatibilidad pero llamar al nuevo método
        generarFacturasFaltantes();
    }

    /**
     * @deprecated Usar generarFacturasFaltantes() en su lugar.
     */
    @Deprecated
    public void generarFacturasMensuales() {
        generarFacturasFaltantes();
    }

    /**
     * Revisa facturas vencidas y programa notificaciones de recordatorio.
     * Solo para clientes POSTPAGO (mes_adelantado = 0).
     */
    public void revisarFacturasVencidas() {
        System.out.println("\n🔍 Revisando facturas vencidas...");

        int diasRecordatorio = configDAO.obtenerValorInt(ConfiguracionDAO.DIAS_RECORDATORIO, 0);

        // Buscar facturas pendientes cuya fecha de vencimiento ya pasó
        String sql = "SELECT f.id_factura, f.id_suscripcion, f.monto_total, f.periodo_mes, f.fecha_vencimiento, " +
                "s.mes_adelantado, c.nombres, c.apellidos, c.telefono, s.codigo_contrato " +
                "FROM factura f " +
                "JOIN suscripcion s ON f.id_suscripcion = s.id_suscripcion " +
                "JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE f.id_estado = 1 " + // 1 = PENDIENTE
                "AND s.activo = 1 " +
                "AND DATEDIFF(CURRENT_DATE(), f.fecha_vencimiento) >= ? " +
                "AND NOT EXISTS (" +
                "   SELECT 1 FROM notificacion_pendiente np " +
                "   WHERE np.id_suscripcion = f.id_suscripcion " +
                "   AND np.tipo = 'RECORDATORIO'" +
                ")";

        int recordatoriosProgramados = 0;

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, diasRecordatorio);
            ResultSet rs = ps.executeQuery();

            int plazoDias = configDAO.obtenerValorInt(ConfiguracionDAO.PLAZO_PAGO_DIAS, 21);

            while (rs.next()) {
                int idSuscripcion = rs.getInt("id_suscripcion");
                String nombreCliente = rs.getString("nombres") + " " + rs.getString("apellidos");
                String telefono = rs.getString("telefono");
                String periodo = rs.getString("periodo_mes");
                double monto = rs.getDouble("monto_total");
                java.sql.Date fechaVenc = rs.getDate("fecha_vencimiento");

                // Fecha límite = fecha vencimiento + plazo
                LocalDate fechaLimite = fechaVenc.toLocalDate().plusDays(plazoDias);

                programarNotificacionRecordatorio(
                        idSuscripcion, nombreCliente, telefono,
                        periodo, monto, fechaLimite);
                recordatoriosProgramados++;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("   📱 " + recordatoriosProgramados + " recordatorios programados.");
    }

    /**
     * Revisa ultimátums vencidos y ejecuta cortes de servicio.
     */
    public void revisarUltimatumsVencidos() {
        System.out.println("\n⚠️ Revisando ultimátums vencidos...");

        int plazoDias = configDAO.obtenerValorInt(ConfiguracionDAO.PLAZO_PAGO_DIAS, 21);

        // Buscar facturas que YA pasaron el plazo de ultimátum (agrupado por suscripción para evitar duplicados)
        String sql = "SELECT f.id_suscripcion, MAX(f.monto_total) as monto_total, MAX(f.periodo_mes) as periodo_mes, " +
                "s.ip_cliente, c.nombres, c.apellidos, s.codigo_contrato " +
                "FROM factura f " +
                "JOIN suscripcion s ON f.id_suscripcion = s.id_suscripcion " +
                "JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE f.id_estado = 1 " + // PENDIENTE
                "AND s.activo = 1 " +
                "AND DATEDIFF(CURRENT_DATE(), f.fecha_vencimiento) > ? " +
                "AND EXISTS (" + // Ya se envió el ultimátum
                "   SELECT 1 FROM notificacion_pendiente np " +
                "   WHERE np.id_suscripcion = f.id_suscripcion " +
                "   AND np.tipo = 'ULTIMATUM' AND np.estado = 'ENVIADO'" +
                ") " +
                "GROUP BY f.id_suscripcion, s.ip_cliente, c.nombres, c.apellidos, s.codigo_contrato";

        int cortesEjecutados = 0;

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, plazoDias);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int idSuscripcion = rs.getInt("id_suscripcion");
                String ipCliente = rs.getString("ip_cliente");
                String nombreCliente = rs.getString("nombres") + " " + rs.getString("apellidos");

                // Intentar cortar el servicio
                if (ipCliente != null && !ipCliente.isEmpty()) {
                    boolean cortado = routerService.cortarServicio(ipCliente);

                    if (cortado) {
                        // Marcar suscripción como inactiva (cortada)
                        suscripcionDAO.cambiarEstado(idSuscripcion, 0);
                        cortesEjecutados++;
                        System.out.println("   🔴 Cortado: " + nombreCliente + " (" + ipCliente + ")");
                    } else {
                        // Crear alerta de corte fallido
                        alertaDAO.crearAlertaCorteFallido(idSuscripcion, nombreCliente,
                                "No se pudo conectar al router");
                    }
                } else {
                    // Sin IP configurada
                    alertaDAO.crearAlertaCorteFallido(idSuscripcion, nombreCliente,
                            "Cliente sin IP configurada");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("   🔴 " + cortesEjecutados + " cortes ejecutados.");
    }

    /**
     * Procesa la cola de notificaciones pendientes y las envía.
     */
    public void procesarNotificacionesPendientes() {
    System.out.println("\n📤 WhatsApp automático: DESHABILITADO (usar CSV manual)");
    return;
    
    /* CÓDIGO ORIGINAL PRESERVADO
    System.out.println("\n📤 Procesando notificaciones pendientes...");

    // MODO PRUEBA: Notificaciones habilitadas SOLO para DNI 44085317
    // Fecha de activación general: 10 de Enero 2026
    LocalDate fechaActivacion = LocalDate.of(2026, 1, 10);
    boolean modoPrueba = LocalDate.now().isBefore(fechaActivacion);

    if (modoPrueba) {
        System.out.println("   🧪 MODO PRUEBA ACTIVO");
        System.out.println("   ⏳ Notificaciones generales deshabilitadas hasta " + fechaActivacion);
        System.out.println("   ✅ Solo se enviarán notificaciones al cliente DNI: 44085317");
    }

    List<NotificacionPendiente> pendientes = notificacionDAO.obtenerPendientes();

    int enviados = 0;
    int sinTelefono = 0;
    int errores = 0;
    int filtrados = 0;

    for (NotificacionPendiente n : pendientes) {
        // FILTRO DE PRUEBA: Solo enviar a DNI 44085317 si estamos en modo prueba
        if (modoPrueba) {
            // Obtener DNI del cliente desde la suscripción
            String dniCliente = suscripcionDAO.obtenerDNICliente(n.getIdSuscripcion());
            if (!"44085317".equals(dniCliente)) {
                filtrados++;
                continue; // Saltar este cliente
            }
            System.out.println("   🎯 Cliente de prueba detectado (DNI: 44085317) - Enviando notificación...");
        }

        if (!n.tieneTelefono()) {
            notificacionDAO.marcarSinTelefono(n.getIdNotificacion());
            alertaDAO.crearAlertaSinTelefono(
                    n.getIdSuscripcion(),
                    n.getNombreCliente(),
                    n.getCodigoContrato());
            sinTelefono++;
            continue;
        }

        boolean exito = whatsAppService.enviarMensaje(n.getTelefono(), n.getMensaje());

        if (exito) {
            notificacionDAO.marcarComoEnviada(n.getIdNotificacion());
            enviados++;

            // Si era RECORDATORIO, programar ULTIMÁTUM
            if (n.getTipo() == TipoNotificacion.RECORDATORIO) {
                int plazoDias = configDAO.obtenerValorInt(ConfiguracionDAO.PLAZO_PAGO_DIAS, 21);
                programarUltimatum(n.getIdSuscripcion(), n.getNombreCliente(),
                        n.getTelefono(), plazoDias);
            }
        } else {
            notificacionDAO.marcarComoError(n.getIdNotificacion());
            errores++;
        }
    }

    System.out.println("   ✅ Enviados: " + enviados);
    if (modoPrueba && filtrados > 0) {
        System.out.println("   🚫 Filtrados (modo prueba): " + filtrados);
    }
    System.out.println("   📵 Sin teléfono: " + sinTelefono);
    System.out.println("   ❌ Errores: " + errores);
    FIN CÓDIGO ORIGINAL */
}

    /**
     * Programa una notificación de recordatorio.
     */
    private void programarNotificacionRecordatorio(int idSuscripcion, String nombreCliente,
            String telefono, String periodo, double monto, LocalDate fechaLimite) {

        if (notificacionDAO.existeNotificacionPendiente(idSuscripcion, TipoNotificacion.RECORDATORIO)) {
            return; // Ya existe una pendiente
        }

        String mensaje = mensajeService.generarRecordatorio(nombreCliente, periodo, monto, fechaLimite);

        NotificacionPendiente n = new NotificacionPendiente();
        n.setIdSuscripcion(idSuscripcion);
        n.setTipo(TipoNotificacion.RECORDATORIO);
        n.setMensaje(mensaje);
        n.setTelefono(telefono);
        n.setFechaProgramada(java.sql.Date.valueOf(LocalDate.now()));

        notificacionDAO.crearNotificacion(n);
    }

    /**
     * Programa una notificación de ultimátum.
     */
    private void programarUltimatum(int idSuscripcion, String nombreCliente,
            String telefono, int diasDespues) {

        if (notificacionDAO.existeNotificacionPendiente(idSuscripcion, TipoNotificacion.ULTIMATUM)) {
            return;
        }

        LocalDate fechaCorte = LocalDate.now().plusDays(2); // Corte 24-48h después
        String mensaje = mensajeService.generarUltimatum(nombreCliente, "Periodo pendiente", 0, fechaCorte);

        NotificacionPendiente n = new NotificacionPendiente();
        n.setIdSuscripcion(idSuscripcion);
        n.setTipo(TipoNotificacion.ULTIMATUM);
        n.setMensaje(mensaje);
        n.setTelefono(telefono);
        n.setFechaProgramada(java.sql.Date.valueOf(LocalDate.now().plusDays(diasDespues)));

        notificacionDAO.crearNotificacion(n);
    }

    /**
     * Procesa un pago recibido.
     * Reactiva el servicio si estaba cortado.
     */
    public void procesarPago(int idSuscripcion, double monto) {
        System.out.println("💰 Procesando pago para suscripción: " + idSuscripcion);

        // 1. Cancelar notificaciones pendientes
        notificacionDAO.cancelarPendientes(idSuscripcion);

        // 2. Verificar si estaba cortado y reconectar
        String sql = "SELECT s.activo, s.ip_cliente, c.nombres, c.apellidos " +
                "FROM suscripcion s JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE s.id_suscripcion = ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int activo = rs.getInt("activo");
                String ipCliente = rs.getString("ip_cliente");
                String nombreCliente = rs.getString("nombres") + " " + rs.getString("apellidos");

                if (activo == 0 && ipCliente != null && !ipCliente.isEmpty()) {
                    // Estaba cortado, reconectar
                    boolean reconectado = routerService.reconectarServicio(ipCliente);

                    if (reconectado) {
                        suscripcionDAO.cambiarEstado(idSuscripcion, 1);
                        System.out.println("🟢 Servicio reconectado: " + nombreCliente);

                        // Opcional: Enviar mensaje de reconexión
                        // String msgReconexion = mensajeService.generarAvisoReconexion(nombreCliente);
                        // whatsAppService.enviarMensaje(telefono, msgReconexion);
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Aplaza el ultimátum de una suscripción.
     */
    public void aplazarUltimatum(int idSuscripcion, int diasAdicionales) {
        System.out.println("⏰ Aplazando ultimátum para suscripción: " + idSuscripcion +
                " (" + diasAdicionales + " días adicionales)");

        // Cancelar el ultimátum actual
        String sql = "DELETE FROM notificacion_pendiente " +
                "WHERE id_suscripcion = ? AND tipo = 'ULTIMATUM' AND estado = 'PENDIENTE'";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Crear nuevo ultimátum con la fecha aplazada
        // Se hará en el siguiente ciclo automáticamente si la deuda sigue pendiente
    }
}
