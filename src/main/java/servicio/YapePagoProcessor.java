package servicio;

import DAO.ClienteDAO;
import DAO.PagoDAO;
import DAO.YapeConfigDAO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Procesador de archivos Excel de Yape para registrar pagos automáticamente.
 * Lee el Excel, extrae DNI del mensaje, valida cliente y registra pago.
 */
public class YapePagoProcessor {

    private final ClienteDAO clienteDAO;
    private final PagoDAO pagoDAO;
    private final YapeConfigDAO yapeConfigDAO;
    private final int idUsuarioSistema = 1; // Usuario "Sistema" para pagos automáticos
    private Date ultimaFechaProcesada;
    private Date nuevaUltimaFecha;

    public YapePagoProcessor() {
        this.clienteDAO = new ClienteDAO();
        this.pagoDAO = new PagoDAO();
        this.yapeConfigDAO = new YapeConfigDAO();
    }

    /**
     * Procesa un archivo Excel de Yape y registra los pagos.
     * 
     * @param archivoExcel Archivo Excel descargado
     * @return Resumen del procesamiento
     */
    public ResumenProcesamiento procesarExcel(File archivoExcel) {
        ResumenProcesamiento resumen = new ResumenProcesamiento();

        System.out.println("\n📊 Procesando Excel: " + archivoExcel.getName());

        // Obtener última fecha procesada para evitar duplicados
        ultimaFechaProcesada = yapeConfigDAO.obtenerUltimaFechaProcesada();
        nuevaUltimaFecha = ultimaFechaProcesada;

        if (ultimaFechaProcesada != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            System.out.println("📅 Última fecha procesada: " + sdf.format(ultimaFechaProcesada));
            System.out.println("   Solo se procesarán transacciones posteriores a esta fecha.");
        } else {
            System.out.println("📅 Primera vez procesando - se procesarán todas las transacciones");
        }

        try (FileInputStream fis = new FileInputStream(archivoExcel);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // Primera hoja

            // Saltar encabezado (asumiendo que empieza en fila 5 según la imagen)
            int filaInicio = 5;
            int totalFilas = sheet.getLastRowNum();

            System.out.println("📋 Total de filas a procesar: " + (totalFilas - filaInicio + 1));

            for (int i = filaInicio; i <= totalFilas; i++) {
                Row fila = sheet.getRow(i);
                if (fila == null)
                    continue;

                try {
                    procesarFila(fila, resumen);
                } catch (Exception e) {
                    System.err.println("⚠️ Error en fila " + (i + 1) + ": " + e.getMessage());
                    resumen.errores++;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error leyendo Excel: " + e.getMessage());
            e.printStackTrace();
        }

        // Actualizar última fecha procesada si hubo transacciones nuevas
        if (nuevaUltimaFecha != null
                && (ultimaFechaProcesada == null || nuevaUltimaFecha.after(ultimaFechaProcesada))) {
            yapeConfigDAO.actualizarUltimaFechaProcesada(nuevaUltimaFecha);
        }

        mostrarResumen(resumen);
        return resumen;
    }

    /**
     * Procesa una fila del Excel.
     */
    private void procesarFila(Row fila, ResumenProcesamiento resumen) {
        // Columnas según la imagen:
        // A: Tipo de Transacción
        // B: Origen
        // C: Destino
        // D: Monto
        // E: Mensaje
        // F: Fecha de operación

        String tipoTransaccion = getCellValueAsString(fila.getCell(0));
        String origen = getCellValueAsString(fila.getCell(1));
        String destino = getCellValueAsString(fila.getCell(2));
        double monto = getCellValueAsDouble(fila.getCell(3));
        String mensaje = getCellValueAsString(fila.getCell(4));
        Date fechaOperacion = getCellValueAsDate(fila.getCell(5));

        resumen.totalFilas++;

        // Filtrar transacciones ya procesadas
        if (ultimaFechaProcesada != null && !fechaOperacion.after(ultimaFechaProcesada)) {
            resumen.yaProcesados++;
            return; // Ya fue procesada anteriormente
        }

        // Actualizar la fecha más reciente encontrada
        if (nuevaUltimaFecha == null || fechaOperacion.after(nuevaUltimaFecha)) {
            nuevaUltimaFecha = fechaOperacion;
        }

        // Solo procesar pagos recibidos (TE PAGÓ o PAGASTE)
        String tipo = tipoTransaccion.toUpperCase().trim();
        if (!tipo.contains("PAGO") && !tipo.contains("PAGASTE")) {
            resumen.ignorados++;
            return;
        }

        // Extraer DNI del mensaje
        String dni = extraerDNI(mensaje);
        if (dni == null) {
            System.out.println("   ℹ️ Sin DNI en mensaje: \"" + mensaje + "\" - Ignorando");
            resumen.sinDNI++;
            return;
        }

        // Buscar cliente por DNI
        Map<String, Object> cliente = clienteDAO.buscarPorDNI(dni);
        if (cliente == null) {
            System.out.println("   ⚠️ DNI " + dni + " no encontrado en BD - Ignorando");
            resumen.dniNoEncontrado++;
            return;
        }

        // Registrar pago con monto mensual para división por meses
        int idCliente = (int) cliente.get("id_cliente");
        String nombreCliente = cliente.get("nombres") + " " + cliente.get("apellidos");
        Double montoMensual = cliente.get("monto_mensual") != null
                ? ((Number) cliente.get("monto_mensual")).doubleValue()
                : null;

        boolean exito = registrarPago(idCliente, nombreCliente, dni, monto, fechaOperacion, montoMensual);

        if (exito) {
            resumen.pagosRegistrados++;
            System.out.println("   ✅ Pago registrado: " + nombreCliente + " (DNI: " + dni + ") - S/. " + monto);
        } else {
            resumen.errores++;
        }
    }

    /**
     * Extrae el DNI del mensaje (busca 8 dígitos consecutivos).
     */
    private String extraerDNI(String mensaje) {
        if (mensaje == null || mensaje.trim().isEmpty()) {
            return null;
        }

        // DEBUG: Ver qué mensaje estamos procesando
        System.out.println("🔍 DEBUG - Mensaje: '" + mensaje + "'");

        // Buscar 8 dígitos consecutivos
        Pattern pattern = Pattern.compile("\\b\\d{8}\\b");
        Matcher matcher = pattern.matcher(mensaje);

        if (matcher.find()) {
            String dni = matcher.group();
            System.out.println("✅ DEBUG - DNI encontrado: " + dni);
            return dni;
        }

        System.out.println("❌ DEBUG - NO se encontró DNI en: '" + mensaje + "'");
        return null;
    }

    /**
     * Registra un pago automáticamente dividiéndolo por meses según el monto
     * mensual del servicio.
     */
    private boolean registrarPago(int idCliente, String nombreCliente, String dni,
            double monto, Date fechaOperacion, Double montoMensual) {
        try {
            // Si no hay monto mensual, usar lógica antigua (distribuir entre deudas)
            if (montoMensual == null || montoMensual <= 0) {
                System.out.println("   ⚠️ Cliente sin suscripción activa - usando distribución simple");
                return registrarPagoSimple(idCliente, nombreCliente, dni, monto, fechaOperacion);
            }

            // Calcular número de meses a pagar
            int mesesAPagar = (int) Math.floor(monto / montoMensual);
            double sobrante = monto - (mesesAPagar * montoMensual);

            if (mesesAPagar == 0) {
                System.out.println(String.format("   ⚠️ Monto insuficiente (S/. %.2f) para un mes completo (S/. %.2f)",
                        monto, montoMensual));
                return false;
            }

            System.out.println(String.format("\\n   💰 Procesando pago de S/. %.2f (%d meses × S/. %.2f):",
                    monto, mesesAPagar, montoMensual));

            // Buscar facturas pendientes del cliente (ordenadas por antigüedad)
            List<Object[]> deudasList = pagoDAO.buscarDeudasPorCliente(dni);

            int facturasPagadas = 0;
            int mesesRestantes = mesesAPagar;
            StringBuilder resumen = new StringBuilder();

            // 1. Pagar facturas pendientes primero
            if (deudasList != null && !deudasList.isEmpty()) {
                for (Object[] deuda : deudasList) {
                    if (mesesRestantes <= 0)
                        break;

                    int idFactura = (int) deuda[0];
                    String periodoMes = (String) deuda[4];

                    // Pagar con el monto mensual
                    boolean exito = pagoDAO.realizarCobro(idFactura, montoMensual, idUsuarioSistema, "YAPE");

                    if (exito) {
                        facturasPagadas++;
                        mesesRestantes--;
                        resumen.append(String.format("      ✅ %s - PAGADA: S/. %.2f\\n",
                                periodoMes, montoMensual));
                    } else {
                        resumen.append(String.format("      ❌ Error pagando %s\\n", periodoMes));
                    }
                }
            }

            // 2. Si quedan meses por pagar, generar facturas adelantadas
            if (mesesRestantes > 0) {
                resumen.append(String.format("\\n      📅 Generando %d pago(s) adelantado(s):\\n", mesesRestantes));

                // Obtener id_suscripcion del cliente
                Integer idSuscripcion = obtenerIdSuscripcion(dni);

                if (idSuscripcion != null) {
                    for (int i = 0; i < mesesRestantes; i++) {
                        // Generar factura adelantada
                        boolean exito = generarFacturaAdelantada(idSuscripcion, montoMensual, i + 1);
                        if (exito) {
                            facturasPagadas++;
                            resumen.append(String.format("      ✅ Mes +%d - ADELANTADO: S/. %.2f\\n",
                                    i + 1, montoMensual));
                        }
                    }
                } else {
                    System.out.println("      ⚠️ No se pudo generar pagos adelantados (sin suscripción)");
                }
            }

            // Mostrar resumen
            System.out.println(resumen.toString());

            if (sobrante > 0) {
                System.out.println(String.format("   ℹ️ Sobrante de S/. %.2f (no alcanza para otro mes)", sobrante));
            }

            if (facturasPagadas > 0) {
                crearNotificacionPago(nombreCliente, dni, monto - sobrante, fechaOperacion);
                return true;
            }

            return false;

        } catch (Exception e) {
            System.err.println("❌ Error registrando pago: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lógica simple de distribución (cuando no hay monto mensual).
     */
    private boolean registrarPagoSimple(int idCliente, String nombreCliente, String dni,
            double monto, Date fechaOperacion) {
        try {
            List<Object[]> deudasList = pagoDAO.buscarDeudasPorCliente(dni);

            if (deudasList == null || deudasList.isEmpty()) {
                System.out.println("   ℹ️ Cliente " + nombreCliente + " no tiene deudas pendientes");
                return false;
            }

            double montoRestante = monto;
            int facturasPagadas = 0;
            StringBuilder resumen = new StringBuilder();
            resumen.append("\\n   💰 Distribuyendo pago de S/. ").append(String.format("%.2f", monto)).append(":\\n");

            for (Object[] deuda : deudasList) {
                if (montoRestante <= 0)
                    break;

                int idFactura = (int) deuda[0];
                String periodoMes = (String) deuda[4];
                double montoFactura = deuda[6] instanceof Integer
                        ? ((Integer) deuda[6]).doubleValue()
                        : (double) deuda[6];

                double montoPagar = Math.min(montoRestante, montoFactura);
                boolean exito = pagoDAO.realizarCobro(idFactura, montoPagar, idUsuarioSistema, "YAPE");

                if (exito) {
                    facturasPagadas++;
                    montoRestante -= montoPagar;
                    String estado = (montoPagar >= montoFactura) ? "PAGADA" : "PAGO PARCIAL";
                    resumen.append(String.format("      ✅ %s - %s: S/. %.2f\\n",
                            periodoMes, estado, montoPagar));
                }
            }

            System.out.println(resumen.toString());
            return facturasPagadas > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtiene el ID de suscripción activa del cliente.
     */
    private Integer obtenerIdSuscripcion(String dni) {
        try {
            Map<String, Object> cliente = clienteDAO.buscarPorDNI(dni);
            if (cliente != null && cliente.get("id_suscripcion") != null) {
                return (Integer) cliente.get("id_suscripcion");
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo id_suscripcion: " + e.getMessage());
        }
        return null;
    }

    /**
     * Genera una factura adelantada y la marca como pagada.
     */
    private boolean generarFacturaAdelantada(int idSuscripcion, double monto, int mesesAdelante) {
        try {
            // Calcular período futuro
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.MONTH, mesesAdelante);

            String periodoMes = new SimpleDateFormat("MMMM yyyy", new java.util.Locale("es", "ES"))
                    .format(cal.getTime());

            java.sql.Date fechaVencimiento = new java.sql.Date(cal.getTimeInMillis());

            // Crear factura adelantada y marcarla como pagada
            return pagoDAO.crearFacturaManual(
                    idSuscripcion,
                    periodoMes,
                    monto,
                    2, // Estado PAGADO
                    fechaVencimiento,
                    true, // Registrar en caja
                    idUsuarioSistema);

        } catch (Exception e) {
            System.err.println("Error generando factura adelantada: " + e.getMessage());
            return false;
        }
    }

    /**
     * Crea una notificación de pago Yape procesado.
     */
    private void crearNotificacionPago(String nombreCliente, String dni,
            double monto, Date fechaOperacion) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            String fechaStr = sdf.format(fechaOperacion);

            String mensaje = String.format(
                    "💰 Pago Yape procesado automáticamente:\n" +
                            "Cliente: %s (DNI: %s)\n" +
                            "Monto: S/. %.2f\n" +
                            "Fecha: %s",
                    nombreCliente, dni, monto, fechaStr);

            // Aquí podrías crear una alerta en la BD si tienes una tabla para eso
            System.out.println("   📢 " + mensaje);

        } catch (Exception e) {
            System.err.println("⚠️ Error creando notificación: " + e.getMessage());
        }
    }

    /**
     * Muestra el resumen del procesamiento.
     */
    private void mostrarResumen(ResumenProcesamiento resumen) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 RESUMEN DE PROCESAMIENTO YAPE");
        System.out.println("=".repeat(60));
        System.out.println("   📋 Total de filas: " + resumen.totalFilas);
        System.out.println("   ✅ Pagos registrados: " + resumen.pagosRegistrados);
        System.out.println("   🔄 Ya procesados (duplicados): " + resumen.yaProcesados);
        System.out.println("   ⏭️ Ignorados (no son pagos): " + resumen.ignorados);
        System.out.println("   📝 Sin DNI en mensaje: " + resumen.sinDNI);
        System.out.println("   ⚠️ DNI no encontrado: " + resumen.dniNoEncontrado);
        System.out.println("   ❌ Errores: " + resumen.errores);
        System.out.println("=".repeat(60) + "\n");
    }

    // Métodos auxiliares para leer celdas

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private double getCellValueAsDouble(Cell cell) {
        if (cell == null)
            return 0.0;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String valor = cell.getStringCellValue().replace(",", ".");
                return Double.parseDouble(valor);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error leyendo monto: " + e.getMessage());
        }

        return 0.0;
    }

    private Date getCellValueAsDate(Cell cell) {
        if (cell == null)
            return new Date();

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                return sdf.parse(cell.getStringCellValue());
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error leyendo fecha: " + e.getMessage());
        }

        return new Date();
    }

    /**
     * Clase para almacenar el resumen del procesamiento.
     */
    public static class ResumenProcesamiento {
        public int totalFilas = 0;
        public int pagosRegistrados = 0;
        public int ignorados = 0;
        public int sinDNI = 0;
        public int dniNoEncontrado = 0;
        public int errores = 0;
        public int yaProcesados = 0; // Transacciones ya procesadas anteriormente
    }
}
