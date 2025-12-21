package servicio;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio para generar mensajes formateados de WhatsApp.
 * Centraliza los textos para mantener consistencia.
 */
public class MensajeTemplateService {

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_MES = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "PE"));

    /**
     * Genera mensaje de RECORDATORIO (primer aviso).
     */
    public String generarRecordatorio(String nombreCliente, String periodoMes, double monto, LocalDate fechaLimite) {
        return String.format(
                "Hola %s 👋\n\n" +
                        "Le recordamos que su pago del servicio de internet correspondiente a *%s* " +
                        "por *S/ %.2f* está pendiente.\n\n" +
                        "📅 Fecha límite de pago: *%s*\n\n" +
                        "Evite la suspensión del servicio realizando su pago a tiempo.\n\n" +
                        "Gracias por preferirnos. 🌐\n" +
                        "_FNET - Internet de Alta Velocidad_",
                nombreCliente,
                periodoMes,
                monto,
                fechaLimite.format(FMT_FECHA));
    }

    /**
     * Genera mensaje de ULTIMÁTUM (aviso final antes del corte).
     */
    public String generarUltimatum(String nombreCliente, String periodoMes, double monto, LocalDate fechaCorte) {
        return String.format(
                "⚠️ *AVISO IMPORTANTE* ⚠️\n\n" +
                        "Estimado/a %s,\n\n" +
                        "Su servicio de internet será *SUSPENDIDO* el día *%s* " +
                        "por falta de pago del periodo *%s*.\n\n" +
                        "💰 Monto pendiente: *S/ %.2f*\n\n" +
                        "Para evitar la suspensión, realice su pago antes de la fecha indicada.\n\n" +
                        "Si ya realizó el pago, por favor ignore este mensaje.\n\n" +
                        "_FNET - Internet de Alta Velocidad_",
                nombreCliente,
                fechaCorte.format(FMT_FECHA),
                periodoMes,
                monto);
    }

    /**
     * Genera mensaje de confirmación de CORTE.
     */
    public String generarAvisoCorte(String nombreCliente, String periodoMes, double monto) {
        return String.format(
                "🔴 *SERVICIO SUSPENDIDO*\n\n" +
                        "Estimado/a %s,\n\n" +
                        "Lamentamos informarle que su servicio de internet ha sido *suspendido* " +
                        "por falta de pago del periodo *%s*.\n\n" +
                        "💰 Deuda pendiente: *S/ %.2f*\n\n" +
                        "Para reconectar su servicio, realice el pago y comuníquese con nosotros.\n\n" +
                        "_FNET - Internet de Alta Velocidad_",
                nombreCliente,
                periodoMes,
                monto);
    }

    /**
     * Genera mensaje de RECONEXIÓN.
     */
    public String generarAvisoReconexion(String nombreCliente) {
        return String.format(
                "🟢 *SERVICIO RECONECTADO*\n\n" +
                        "Estimado/a %s,\n\n" +
                        "¡Su servicio de internet ha sido *reconectado* exitosamente!\n\n" +
                        "Gracias por regularizar su pago. 🙏\n\n" +
                        "_FNET - Internet de Alta Velocidad_",
                nombreCliente);
    }

    /**
     * Formatea el nombre del periodo (mes año).
     * Ejemplo: "Enero 2025"
     */
    public String formatearPeriodo(LocalDate fecha) {
        String mes = fecha.format(FMT_MES);
        // Capitalizar primera letra
        return mes.substring(0, 1).toUpperCase() + mes.substring(1);
    }

    /**
     * Formatea el nombre del periodo a partir de un String "Enero 2025".
     * Solo limpia y capitaliza.
     */
    public String limpiarPeriodo(String periodo) {
        if (periodo == null || periodo.isEmpty())
            return "Mes desconocido";
        return periodo.trim();
    }
}
