package DAO;

import bd.Conexion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PagoDAO {

    // 1. Buscar deudas (Actualizado para traer id_suscripcion)
    public List<Object[]> buscarDeudasPorCliente(String textoBusqueda) {
        List<Object[]> lista = new ArrayList<>();
        // AGREGAMOS: sus.id_suscripcion (índice 7 en el array resultante)
        String sql = "SELECT f.id_factura, c.nombres, c.apellidos, s.descripcion, f.periodo_mes, f.monto_total, f.fecha_vencimiento, sus.id_suscripcion "
                +
                "FROM factura f " +
                "JOIN suscripcion sus ON f.id_suscripcion = sus.id_suscripcion " +
                "JOIN cliente c ON sus.id_cliente = c.id_cliente " +
                "JOIN servicio s ON sus.id_servicio = s.id_servicio " +
                "WHERE f.id_estado = 1 " + // PENDIENTE
                "AND (c.dni_cliente LIKE ? OR c.apellidos LIKE ? OR c.nombres LIKE ?) " +
                "ORDER BY f.fecha_vencimiento ASC"; // IMPORTANTE: Ordenar por antigüedad

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            String parametro = "%" + textoBusqueda + "%";
            ps.setString(1, parametro);
            ps.setString(2, parametro);
            ps.setString(3, parametro);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new Object[] {
                        rs.getInt("id_factura"),
                        rs.getString("nombres") + " " + rs.getString("apellidos"),
                        rs.getString("descripcion"),
                        rs.getString("periodo_mes"),
                        rs.getDouble("monto_total"),
                        rs.getDate("fecha_vencimiento"),
                        rs.getInt("id_suscripcion") // DATO NUEVO
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    // 2. Realizar Cobro - Mejorado para soportar pagos parciales y registrar en
    // tabla pago
    public boolean realizarCobro(int idFactura, double monto, int idUsuario) {
        return realizarCobro(idFactura, monto, idUsuario, "EFECTIVO");
    }

    // Sobrecarga con método de pago
    public boolean realizarCobro(int idFactura, double monto, int idUsuario, String metodoPago) {
        Connection conn = null;
        try {
            conn = Conexion.getConexion();
            conn.setAutoCommit(false);

            // 1. Obtener monto total de la factura y monto ya pagado
            String sqlConsulta = "SELECT monto, COALESCE(monto_pagado, 0) as monto_pagado FROM factura WHERE id_factura = ?";
            double montoTotal = 0;
            double montoPagadoAnterior = 0;

            try (PreparedStatement psConsulta = conn.prepareStatement(sqlConsulta)) {
                psConsulta.setInt(1, idFactura);
                ResultSet rs = psConsulta.executeQuery();
                if (rs.next()) {
                    montoTotal = rs.getDouble("monto");
                    montoPagadoAnterior = rs.getDouble("monto_pagado");
                }
            }

            // 2. Calcular nuevo monto pagado
            double nuevoMontoPagado = montoPagadoAnterior + monto;
            boolean pagadoCompleto = nuevoMontoPagado >= montoTotal;

            // 3. Actualizar factura (monto_pagado y estado si está completo)
            String sqlFactura;
            if (pagadoCompleto) {
                sqlFactura = "UPDATE factura SET id_estado = 2, monto_pagado = ?, fecha_pago = NOW() WHERE id_factura = ?";
            } else {
                sqlFactura = "UPDATE factura SET monto_pagado = ? WHERE id_factura = ?";
            }

            try (PreparedStatement psFactura = conn.prepareStatement(sqlFactura)) {
                psFactura.setDouble(1, nuevoMontoPagado);
                psFactura.setInt(2, idFactura);
                psFactura.executeUpdate();
            }

            // 4. Registrar en tabla PAGO (usando columnas reales de la tabla)
            String sqlPago = "INSERT INTO pago (monto, fecha_pago, metodo_pago, id_empleado) VALUES (?, NOW(), ?, ?)";
            try (PreparedStatement psPago = conn.prepareStatement(sqlPago)) {
                psPago.setDouble(1, monto);
                psPago.setString(2, metodoPago);
                psPago.setInt(3, idUsuario);
                psPago.executeUpdate();
            }

            // 5. Registrar en movimiento_caja
            String sqlCaja = "INSERT INTO movimiento_caja (fecha, monto, descripcion, id_categoria, id_usuario) " +
                    "VALUES (NOW(), ?, CONCAT('Cobro Factura #', ?), 1, ?)";
            try (PreparedStatement psCaja = conn.prepareStatement(sqlCaja)) {
                psCaja.setDouble(1, monto);
                psCaja.setInt(2, idFactura);
                psCaja.setInt(3, idUsuario);
                psCaja.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (Exception e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (Exception ex) {
            }
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (Exception ex) {
            }
        }
    }

    // EN: DAO/PagoDAO.java
    // 3. GENERACIÓN DE FACTURA
    // LÓGICA DEL NEGOCIO:
    // - PREPAGO: Cobra período ADELANTE (ej: 20 Dic a 20 Ene = concepto Enero)
    // - POSTPAGO: Cobra período ATRÁS (ej: 20 Nov a 20 Dic = concepto Diciembre)
    public boolean generarSiguienteFactura(int idSuscripcion) {
        Connection conn = null;
        try {
            conn = Conexion.getConexion();

            // A. Obtener datos del contrato
            String sqlInfo = "SELECT s.mes_adelantado, s.dia_pago, serv.mensualidad " +
                    "FROM suscripcion s " +
                    "JOIN servicio serv ON s.id_servicio = serv.id_servicio " +
                    "WHERE s.id_suscripcion = ?";

            boolean esMesAdelantado = true;
            int diaPago = LocalDate.now().getDayOfMonth();
            double montoMensual = 0.0;

            try (PreparedStatement psInfo = conn.prepareStatement(sqlInfo)) {
                psInfo.setInt(1, idSuscripcion);
                ResultSet rsInfo = psInfo.executeQuery();
                if (rsInfo.next()) {
                    esMesAdelantado = rsInfo.getInt("mes_adelantado") == 1;
                    diaPago = rsInfo.getInt("dia_pago");
                    montoMensual = rsInfo.getDouble("mensualidad");
                }
            }

            LocalDate hoy = LocalDate.now();
            int diaHoy = hoy.getDayOfMonth();

            // B. VALIDACIÓN: Solo generar si ya llegó o pasó el día de pago
            if (diaHoy < diaPago) {
                System.out.println("   ⏳ Suscripción " + idSuscripcion + " - día de pago es " + diaPago
                        + ", hoy es " + diaHoy + ". Esperar.");
                return false;
            }

            // C. Calcular RANGO DE FECHAS y CONCEPTO según tipo
            LocalDate fechaInicio, fechaFin, fechaVencimiento;
            String nombrePeriodo, rangoPeriodo;
            DateTimeFormatter fmtMes = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES"));
            DateTimeFormatter fmtRango = DateTimeFormatter.ofPattern("dd MMM", new Locale("es", "ES")); // Formato
                                                                                                        // legible: "17
                                                                                                        // dic"

            if (esMesAdelantado) {
                // PREPAGO: Cobra período ADELANTE
                // Rango: dia_pago del mes actual → dia_pago del mes siguiente
                // Ejemplo: 20 Dic → 20 Ene = concepto Enero (mes donde cae más del período)
                fechaInicio = hoy.withDayOfMonth(Math.min(diaPago, hoy.lengthOfMonth()));
                LocalDate mesSiguiente = hoy.plusMonths(1);
                fechaFin = mesSiguiente.withDayOfMonth(Math.min(diaPago, mesSiguiente.lengthOfMonth()));
                fechaVencimiento = fechaInicio; // Vence al inicio del período

                // El concepto es el mes donde cae la MAYOR parte del período
                // Para dia_pago <= 16: la mayoría cae en el mes actual
                // Para dia_pago > 16: la mayoría cae en el mes siguiente
                LocalDate mesConcepto = (diaPago <= 16) ? fechaInicio : fechaFin;
                String mesNombre = mesConcepto.format(fmtMes);
                nombrePeriodo = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1).toLowerCase();

            } else {
                // POSTPAGO: Cobra período ATRÁS
                // Rango: dia_pago del mes anterior → dia_pago del mes actual
                // Ejemplo: 20 Nov → 20 Dic = concepto Diciembre (mes donde cae más del período)
                LocalDate mesAnterior = hoy.minusMonths(1);
                fechaInicio = mesAnterior.withDayOfMonth(Math.min(diaPago, mesAnterior.lengthOfMonth()));
                fechaFin = hoy.withDayOfMonth(Math.min(diaPago, hoy.lengthOfMonth()));
                fechaVencimiento = fechaFin; // Vence al final del período

                // El concepto es el mes donde cae la MAYOR parte del período
                // Para dia_pago <= 16: la mayoría cae en el mes anterior
                // Para dia_pago > 16: la mayoría cae en el mes actual
                LocalDate mesConcepto = (diaPago <= 16) ? fechaInicio : fechaFin;
                String mesNombre = mesConcepto.format(fmtMes);
                nombrePeriodo = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1).toLowerCase();
            }

            // Formato del rango: "17 dic - 17 ene"
            rangoPeriodo = fechaInicio.format(fmtRango) + " - " + fechaFin.format(fmtRango);

            // DEBUG: Mostrar qué periodo se está calculando
            System.out.println("   🔍 DEBUG Suscripción " + idSuscripcion + ":");
            System.out.println("      - Día pago: " + diaPago + ", Hoy: " + hoy);
            System.out.println("      - Tipo: " + (esMesAdelantado ? "PREPAGO" : "POSTPAGO"));
            System.out.println("      - Rango calculado: " + rangoPeriodo);
            System.out.println("      - Periodo calculado: " + nombrePeriodo);

            // D. Verificar que no exista factura para este periodo (ÚNICA VALIDACIÓN
            // CONFIABLE)
            String sqlExiste = "SELECT COUNT(*) FROM factura WHERE id_suscripcion = ? AND periodo_mes = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlExiste)) {
                ps.setInt(1, idSuscripcion);
                ps.setString(2, nombrePeriodo);

                System.out.println("      - SQL: " + sqlExiste);
                System.out.println("      - Parámetros: id_suscripcion=" + idSuscripcion + ", periodo_mes='"
                        + nombrePeriodo + "'");

                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    int count = rs.getInt(1);
                    System.out.println("      ❌ Ya existe " + count + " factura(s) para periodo: " + nombrePeriodo);
                    return false;
                } else {
                    System.out.println("      ✅ No existe factura para " + nombrePeriodo + " - Generando...");
                }
            }

            // F. Insertar Factura (con rango_periodo) - codigo_factura se actualiza después
            String sqlInsert = "INSERT INTO factura (id_suscripcion, fecha_emision, fecha_vencimiento, monto_total, monto_pagado, id_estado, codigo_factura, periodo_mes, rango_periodo) "
                    +
                    "VALUES (?, NOW(), ?, ?, 0.00, 1, '', ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idSuscripcion);
                ps.setDate(2, java.sql.Date.valueOf(fechaVencimiento));
                ps.setDouble(3, montoMensual);
                ps.setString(4, nombrePeriodo);
                ps.setString(5, rangoPeriodo);

                boolean insertado = ps.executeUpdate() > 0;

                if (insertado) {
                    // Obtener el id_factura generado y actualizar codigo_factura
                    ResultSet rsKeys = ps.getGeneratedKeys();
                    if (rsKeys.next()) {
                        int idFactura = rsKeys.getInt(1);
                        String codigoFactura = String.format("%04d", idFactura); // 0001, 0002, etc.

                        String sqlUpdateCodigo = "UPDATE factura SET codigo_factura = ? WHERE id_factura = ?";
                        try (PreparedStatement psUpd = conn.prepareStatement(sqlUpdateCodigo)) {
                            psUpd.setString(1, codigoFactura);
                            psUpd.setInt(2, idFactura);
                            psUpd.executeUpdate();
                        }
                    }

                    String tipo = esMesAdelantado ? "PREPAGO" : "POSTPAGO";
                    System.out.println("   [" + tipo + "] " + nombrePeriodo + " (" + rangoPeriodo + ")");
                }
                return insertado;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (Exception e) {
            }
        }
    }

    // En src/DAO/PagoDAO.java
    public List<Object[]> obtenerHistorialCompleto(int idSuscripcion) {
        List<Object[]> lista = new ArrayList<>();
        // Traemos todo: Pagados (2), Pendientes (1), Anulados (0)
        // Incluimos rango_periodo (puede ser NULL en registros antiguos)
        String sql = "SELECT periodo_mes, fecha_vencimiento, monto_total, monto_pagado, fecha_pago, id_estado, " +
                "COALESCE(rango_periodo, '') as rango_periodo " +
                "FROM factura WHERE id_suscripcion = ? ORDER BY fecha_vencimiento DESC";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String estado = "DESCONOCIDO";
                int idEst = rs.getInt("id_estado");
                if (idEst == 1)
                    estado = "PENDIENTE";
                else if (idEst == 2)
                    estado = "PAGADO";
                else if (idEst == 0)
                    estado = "ANULADO";

                lista.add(new Object[] {
                        rs.getString("periodo_mes"),
                        rs.getDate("fecha_vencimiento"),
                        rs.getDouble("monto_total"),
                        estado,
                        rs.getDate("fecha_pago"), // Puede ser null
                        rs.getString("rango_periodo") // Nueva columna
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    // --- AGREGAR ESTO EN PagoDAO.java ---

    // Obtener los últimos N pagos registrados (Para el Dashboard)
    public List<Object[]> obtenerUltimosPagos(int limite) {
        List<Object[]> lista = new ArrayList<>();
        // Unimos Factura -> Suscripción -> Cliente para tener el nombre
        // Filtramos por id_estado = 2 (Pagado)
        String sql = "SELECT f.fecha_pago, c.nombres, c.apellidos, f.monto_pagado " +
                "FROM factura f " +
                "JOIN suscripcion s ON f.id_suscripcion = s.id_suscripcion " +
                "JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE f.id_estado = 2 " +
                "ORDER BY f.fecha_pago DESC LIMIT ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limite);
            ResultSet rs = ps.executeQuery();

            // Formato de hora simple
            java.text.SimpleDateFormat sdfHora = new java.text.SimpleDateFormat("HH:mm");

            while (rs.next()) {
                java.sql.Timestamp fechaPago = rs.getTimestamp("fecha_pago");
                String hora = (fechaPago != null) ? sdfHora.format(fechaPago) : "--:--";

                lista.add(new Object[] {
                        hora, // Col 1: Hora
                        rs.getString("nombres") + " " + rs.getString("apellidos"), // Col 2: Cliente
                        "S/. " + rs.getDouble("monto_pagado"), // Col 3: Monto
                        "Efectivo" // Col 4: Método (Hardcodeado por ahora)
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    // En src/DAO/PagoDAO.java
    public List<Object[]> buscarDeudasPorSuscripcion(int idSuscripcion) {
        List<Object[]> lista = new ArrayList<>();
        // Solo facturas pendientes (id_estado = 1)
        String sql = "SELECT id_factura, periodo_mes, monto_total, fecha_vencimiento " +
                "FROM factura WHERE id_suscripcion = ? AND id_estado = 1 ORDER BY fecha_vencimiento ASC";

        try (java.sql.Connection conn = bd.Conexion.getConexion();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Object[] {
                            rs.getInt("id_factura"),
                            rs.getString("periodo_mes"),
                            rs.getDouble("monto_total"),
                            rs.getDate("fecha_vencimiento")
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ============================================================
    // NUEVOS MÉTODOS PARA GESTIÓN DE HISTORIAL EDITABLE
    // ============================================================

    /**
     * Obtiene el historial completo de facturas para edición.
     * Incluye el ID de factura para poder editarlas.
     */
    public List<Object[]> obtenerHistorialEditable(int idSuscripcion) {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT id_factura, periodo_mes, fecha_vencimiento, monto_total, monto_pagado, fecha_pago, id_estado, "
                +
                "COALESCE(rango_periodo, '') as rango_periodo " +
                "FROM factura WHERE id_suscripcion = ? ORDER BY fecha_emision DESC, id_factura DESC";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int idEstado = rs.getInt("id_estado");
                String estado = idEstado == 2 ? "PAGADO" : (idEstado == 1 ? "PENDIENTE" : "ANULADO");

                lista.add(new Object[] {
                        rs.getInt("id_factura"), // 0: ID (para edición)
                        rs.getString("periodo_mes"), // 1: Periodo
                        rs.getString("rango_periodo"), // 2: Rango (NUEVO)
                        rs.getDate("fecha_vencimiento"), // 3: Vencimiento
                        rs.getDouble("monto_total"), // 4: Monto
                        estado, // 5: Estado texto
                        rs.getDate("fecha_pago"), // 6: Fecha pago (puede ser null)
                        idEstado // 7: ID Estado numérico
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Actualiza el estado de una factura.
     * Si cambia a PAGADO (2), registra automáticamente en movimiento_caja.
     * Si cambia a PENDIENTE (1), NO elimina el movimiento de caja (los registros
     * financieros son permanentes).
     */
    public boolean actualizarEstadoFactura(int idFactura, int nuevoEstado, java.sql.Date fechaPago, int idUsuario) {
        Connection conn = null;
        try {
            conn = Conexion.getConexion();
            conn.setAutoCommit(false);

            // 1. Obtener el monto de la factura para el movimiento de caja
            double monto = 0;
            String periodoMes = "";
            String sqlInfo = "SELECT monto_total, periodo_mes FROM factura WHERE id_factura = ?";
            try (PreparedStatement psInfo = conn.prepareStatement(sqlInfo)) {
                psInfo.setInt(1, idFactura);
                ResultSet rs = psInfo.executeQuery();
                if (rs.next()) {
                    monto = rs.getDouble("monto_total");
                    periodoMes = rs.getString("periodo_mes");
                }
            }

            // 2. Actualizar la factura
            String sqlUpdate = "UPDATE factura SET id_estado = ?, fecha_pago = ?, monto_pagado = ? WHERE id_factura = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setInt(1, nuevoEstado);
                if (nuevoEstado == 2 && fechaPago != null) {
                    ps.setDate(2, fechaPago);
                    ps.setDouble(3, monto);
                } else {
                    ps.setNull(2, java.sql.Types.DATE);
                    ps.setDouble(3, 0);
                }
                ps.setInt(4, idFactura);
                ps.executeUpdate();
            }

            // 3. Si se marca como PAGADO, registrar en movimiento_caja
            if (nuevoEstado == 2) {
                String sqlCaja = "INSERT INTO movimiento_caja (fecha, monto, descripcion, id_categoria, id_usuario) " +
                        "VALUES (?, ?, ?, 1, ?)";
                try (PreparedStatement psCaja = conn.prepareStatement(sqlCaja)) {
                    psCaja.setDate(1, fechaPago != null ? fechaPago : new java.sql.Date(System.currentTimeMillis()));
                    psCaja.setDouble(2, monto);
                    psCaja.setString(3, "Cobro Factura #" + idFactura + " - " + periodoMes);
                    psCaja.setInt(4, idUsuario);
                    psCaja.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (Exception ex) {
            }
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (Exception ex) {
            }
        }
    }

    /**
     * Crea una factura manual (para migración de datos o nuevo contrato).
     * 
     * @param registrarEnCaja Si es true y estado=PAGADO, registra en
     *                        movimiento_caja
     * @param rangoPeriodo    Rango del periodo (ej: "20 Dic - 20 Ene"), puede ser
     *                        null
     */
    public boolean crearFacturaManual(int idSuscripcion, String periodoMes, double monto,
            int estado, java.sql.Date fechaVencimiento,
            boolean registrarEnCaja, int idUsuario, String rangoPeriodo) {
        Connection conn = null;
        try {
            conn = Conexion.getConexion();
            conn.setAutoCommit(false);

            String sqlInsert = "INSERT INTO factura (id_suscripcion, fecha_emision, fecha_vencimiento, " +
                    "monto_total, monto_pagado, id_estado, codigo_factura, periodo_mes, fecha_pago, rango_periodo) " +
                    "VALUES (?, NOW(), ?, ?, ?, ?, CONCAT('MIG-', FLOOR(RAND()*100000)), ?, ?, ?)";

            java.sql.Date fechaPago = (estado == 2) ? fechaVencimiento : null;
            double montoPagado = (estado == 2) ? monto : 0;

            int idFacturaGenerada = -1;
            try (PreparedStatement ps = conn.prepareStatement(sqlInsert, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idSuscripcion);
                ps.setDate(2, fechaVencimiento);
                ps.setDouble(3, monto);
                ps.setDouble(4, montoPagado);
                ps.setInt(5, estado);
                ps.setString(6, periodoMes);
                ps.setDate(7, fechaPago);
                ps.setString(8, rangoPeriodo); // Puede ser null
                ps.executeUpdate();

                ResultSet rsKeys = ps.getGeneratedKeys();
                if (rsKeys.next()) {
                    idFacturaGenerada = rsKeys.getInt(1);
                }
            }

            // Registrar en caja si corresponde
            if (registrarEnCaja && estado == 2 && idFacturaGenerada > 0) {
                String sqlCaja = "INSERT INTO movimiento_caja (fecha, monto, descripcion, id_categoria, id_usuario) " +
                        "VALUES (?, ?, ?, 1, ?)";
                try (PreparedStatement psCaja = conn.prepareStatement(sqlCaja)) {
                    psCaja.setDate(1, fechaVencimiento);
                    psCaja.setDouble(2, monto);
                    psCaja.setString(3, "Pago - " + periodoMes + " (Factura #" + idFacturaGenerada + ")");
                    psCaja.setInt(4, idUsuario);
                    psCaja.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (Exception ex) {
            }
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (Exception ex) {
            }
        }
    }

    /**
     * Versión de compatibilidad sin rango_periodo (para código existente).
     */
    public boolean crearFacturaManual(int idSuscripcion, String periodoMes, double monto,
            int estado, java.sql.Date fechaVencimiento,
            boolean registrarEnCaja, int idUsuario) {
        return crearFacturaManual(idSuscripcion, periodoMes, monto, estado,
                fechaVencimiento, registrarEnCaja, idUsuario, null);
    }

    /**
     * Actualiza datos de una factura existente (periodo, monto, vencimiento).
     * NO modifica el estado ni la integración con caja.
     */
    public boolean actualizarFactura(int idFactura, String periodoMes, double monto, java.sql.Date fechaVencimiento) {
        String sql = "UPDATE factura SET periodo_mes = ?, monto_total = ?, fecha_vencimiento = ? WHERE id_factura = ?";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, periodoMes);
            ps.setDouble(2, monto);
            ps.setDate(3, fechaVencimiento);
            ps.setInt(4, idFactura);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Elimina una factura. Usar con precaución.
     * NOTA: No elimina el movimiento de caja asociado (registros financieros son
     * permanentes).
     */
    public boolean eliminarFactura(int idFactura) {
        String sql = "DELETE FROM factura WHERE id_factura = ?";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idFactura);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Genera los 12 meses de un año para una suscripción.
     * Útil para migrar clientes desde Excel con su historial completo.
     * 
     * @return Cantidad de meses creados exitosamente
     */
    public int generarAnioCompleto(int idSuscripcion, int anio, int diaPago, double monto,
            int estadoPorDefecto, boolean registrarEnCaja, int idUsuario) {
        int mesesCreados = 0;
        String[] nombresMeses = { "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre" };

        for (int mes = 1; mes <= 12; mes++) {
            String periodoMes = nombresMeses[mes - 1] + " " + anio;

            // Calcular fecha de vencimiento
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(anio, mes - 1, Math.min(diaPago, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)));
            java.sql.Date fechaVenc = new java.sql.Date(cal.getTimeInMillis());

            // Verificar si ya existe este periodo
            if (!existeFacturaPeriodo(idSuscripcion, periodoMes)) {
                boolean creada = crearFacturaManual(idSuscripcion, periodoMes, monto,
                        estadoPorDefecto, fechaVenc,
                        registrarEnCaja, idUsuario);
                if (creada)
                    mesesCreados++;
            }
        }
        return mesesCreados;
    }

    /**
     * Verifica si ya existe una factura para un periodo específico.
     */
    private boolean existeFacturaPeriodo(int idSuscripcion, String periodoMes) {
        String sql = "SELECT COUNT(*) FROM factura WHERE id_suscripcion = ? AND periodo_mes = ?";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ps.setString(2, periodoMes);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Obtiene todos los datos necesarios para regenerar una boleta PDF.
     * 
     * @param idFactura ID de la factura
     * @return Map con todos los datos, o null si no existe
     */
    public java.util.Map<String, Object> obtenerDatosParaBoleta(int idFactura) {
        String sql = "SELECT f.id_factura, f.monto_total, f.monto_pagado, f.periodo_mes, " +
                "f.fecha_vencimiento, f.fecha_emision, f.id_estado, " +
                "CONCAT(c.nombres, ' ', c.apellidos) as nombre_cliente, " +
                "sus.id_suscripcion, sus.codigo_contrato, sus.direccion_instalacion, " +
                "s.descripcion as plan_servicio, s.mensualidad " +
                "FROM factura f " +
                "JOIN suscripcion sus ON f.id_suscripcion = sus.id_suscripcion " +
                "JOIN cliente c ON sus.id_cliente = c.id_cliente " +
                "JOIN servicio s ON sus.id_servicio = s.id_servicio " +
                "WHERE f.id_factura = ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idFactura);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                java.util.Map<String, Object> datos = new java.util.HashMap<>();
                datos.put("idFactura", rs.getInt("id_factura"));
                datos.put("nombreCliente", rs.getString("nombre_cliente"));
                datos.put("codigoContrato", rs.getString("codigo_contrato"));
                datos.put("direccion", rs.getString("direccion_instalacion"));
                datos.put("concepto", rs.getString("periodo_mes"));
                datos.put("planServicio", rs.getString("plan_servicio"));
                datos.put("monto",
                        rs.getDouble("monto_pagado") > 0 ? rs.getDouble("monto_pagado") : rs.getDouble("monto_total"));
                datos.put("fechaVencimiento", rs.getDate("fecha_vencimiento"));
                datos.put("fechaEmision", rs.getDate("fecha_emision"));
                datos.put("idEstado", rs.getInt("id_estado"));
                datos.put("idSuscripcion", rs.getInt("id_suscripcion"));
                return datos;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Cuenta cuántas facturas pendientes tiene una suscripción.
     * Usado para determinar si enviar advertencia de corte (3+ meses).
     */
    public int contarFacturasPendientes(int idSuscripcion) {
        String sql = "SELECT COUNT(*) as total FROM factura WHERE id_suscripcion = ? AND id_estado = 1";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("Error contando facturas pendientes: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Obtiene información de la última factura generada para una suscripción.
     * Retorna formato: "del mes de Enero 2026 (02 Ene - 02 Feb)"
     */
    public String obtenerUltimaFacturaInfo(int idSuscripcion) {
        String sql = "SELECT periodo_mes, rango_periodo FROM factura " +
                "WHERE id_suscripcion = ? ORDER BY id_factura DESC LIMIT 1";

        try (Connection con = bd.Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String periodoMes = rs.getString("periodo_mes");
                String rangoPeriodo = rs.getString("rango_periodo");
                return "del mes de " + periodoMes + " (" + rangoPeriodo + ")";
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo info de factura: " + e.getMessage());
        }

        return "del mes actual";
    }

    /**
     * Obtiene el detalle de todas las facturas pendientes de una suscripción.
     * Retorna formato: "• Enero 2026 (02 Ene - 02 Feb): S/. 50.00\n• Febrero
     * 2026..."
     */
    public String obtenerFacturasPendientesDetalle(int idSuscripcion) {
        String sql = "SELECT periodo_mes, rango_periodo, monto FROM factura " +
                "WHERE id_suscripcion = ? AND id_estado = 1 " +
                "ORDER BY fecha_emision ASC";

        StringBuilder detalle = new StringBuilder();

        try (Connection con = bd.Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String periodoMes = rs.getString("periodo_mes");
                String rangoPeriodo = rs.getString("rango_periodo");
                double monto = rs.getDouble("monto");

                detalle.append("• ").append(periodoMes)
                        .append(" (").append(rangoPeriodo).append("): S/. ")
                        .append(String.format("%.2f", monto))
                        .append("\n");
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo facturas pendientes: " + e.getMessage());
        }

        return detalle.toString().trim();
    }

    /**
     * Obtiene los movimientos de caja del día actual.
     * Retorna: [fecha_pago, cliente, concepto, metodo_pago, monto]
     * 
     * Primero intenta usar la tabla 'pago' (más precisa).
     * Si la tabla no existe, usa 'movimiento_caja' como fallback.
     */
    public Object[][] obtenerMovimientosDelDia() {
        java.util.List<Object[]> movimientos = new java.util.ArrayList<>();

        // Usar movimiento_caja que tiene la info completa de cobros
        // La tabla pago no tiene id_factura para hacer JOIN con clientes
        String sql = "SELECT mc.fecha, mc.descripcion, mc.monto " +
                "FROM movimiento_caja mc " +
                "WHERE DATE(mc.fecha) = CURDATE() " +
                "AND mc.id_categoria = 1 " + // Categoria 1 = Cobros
                "ORDER BY mc.fecha DESC";

        try (Connection con = bd.Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String descripcion = rs.getString("descripcion");
                String metodo = "EFECTIVO";

                // Detectar método de pago por descripción
                if (descripcion != null && descripcion.toUpperCase().contains("YAPE")) {
                    metodo = "YAPE";
                }

                movimientos.add(new Object[] {
                        rs.getTimestamp("fecha"),
                        descripcion, // cliente/concepto
                        descripcion, // concepto
                        metodo,
                        rs.getDouble("monto")
                });
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo movimientos del día: " + e.getMessage());
        }

        return movimientos.toArray(new Object[0][]);
    }

    /**
     * Fallback: Obtiene movimientos del día desde movimiento_caja.
     * Usado cuando la tabla 'pago' no tiene la estructura esperada.
     */
    private Object[][] obtenerMovimientosDelDiaFallback() {
        java.util.List<Object[]> movimientos = new java.util.ArrayList<>();

        // Consulta simplificada usando solo movimiento_caja
        // La descripción contiene info como "Cobro Factura #123" o "Pago - Enero 2026
        // (Factura #456)"
        String sql = "SELECT mc.fecha, mc.descripcion, mc.monto " +
                "FROM movimiento_caja mc " +
                "WHERE DATE(mc.fecha) = CURDATE() " +
                "AND mc.id_categoria = 1 " + // Solo ingresos de cobros
                "ORDER BY mc.fecha DESC";

        try (Connection con = bd.Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String descripcion = rs.getString("descripcion");

                // Extraer nombre de cliente de la descripción si es posible
                // Formato esperado: "Cobro Factura #123" o similar
                String cliente = descripcion;
                String concepto = descripcion;
                String metodo = "EFECTIVO";

                // Si la descripción menciona Yape, marcar como tal
                if (descripcion != null && descripcion.toUpperCase().contains("YAPE")) {
                    metodo = "YAPE";
                }

                movimientos.add(new Object[] {
                        rs.getTimestamp("fecha"),
                        cliente,
                        concepto,
                        metodo,
                        rs.getDouble("monto")
                });
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo movimientos (fallback): " + e.getMessage());
        }

        return movimientos.toArray(new Object[0][]);
    }
}