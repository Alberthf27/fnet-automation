package servicio;

import DAO.ClienteDAO;
import DAO.PagoDAO;
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
    private final int idUsuarioSistema = 1; // Usuario "Sistema" para pagos automáticos

    public YapePagoProcessor() {
        this.clienteDAO = new ClienteDAO();
        this.pagoDAO = new PagoDAO();
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

        // Solo procesar pagos recibidos (PAGASTE significa que te pagaron)
        if (!"PAGASTE".equalsIgnoreCase(tipoTransaccion)) {
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

        // Registrar pago
        int idCliente = (int) cliente.get("id_cliente");
        String nombreCliente = cliente.get("nombres") + " " + cliente.get("apellidos");

        boolean exito = registrarPago(idCliente, nombreCliente, dni, monto, fechaOperacion);

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

        // Buscar 8 dígitos consecutivos
        Pattern pattern = Pattern.compile("\\b\\d{8}\\b");
        Matcher matcher = pattern.matcher(mensaje);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * Registra un pago automáticamente.
     */
    private boolean registrarPago(int idCliente, String nombreCliente, String dni,
            double monto, Date fechaOperacion) {
        try {
            // Buscar facturas pendientes del cliente
            List<Object[]> deudasList = pagoDAO.buscarDeudasPorCliente(dni);

            if (deudasList == null || deudasList.isEmpty()) {
                System.out.println("   ℹ️ Cliente " + nombreCliente + " no tiene deudas pendientes");
                return false;
            }

            // Registrar pago en la primera factura pendiente
            Object[] primeraDeuda = deudasList.get(0);
            int idFactura = (int) primeraDeuda[0];
            double montoFactura = (double) primeraDeuda[6];

            // Realizar cobro
            boolean exito = pagoDAO.realizarCobro(idFactura, monto, idUsuarioSistema);

            if (exito) {
                // Crear notificación de pago procesado
                crearNotificacionPago(nombreCliente, dni, monto, fechaOperacion);
            }

            return exito;

        } catch (Exception e) {
            System.err.println("❌ Error registrando pago: " + e.getMessage());
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
    }
}
