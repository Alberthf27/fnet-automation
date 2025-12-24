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

    // 2. Realizar Cobro (Igual que antes, lo mantienes)
    public boolean realizarCobro(int idFactura, double monto, int idUsuario) {
        // ... (Tu código actual de cobrar está bien) ...
        // Solo asegúrate de que use id_estado = 2 para PAGADO
        Connection conn = null;
        try {
            conn = Conexion.getConexion();
            conn.setAutoCommit(false);

            String sql1 = "UPDATE factura SET id_estado = 2, monto_pagado = ? WHERE id_factura = ?";
            try (PreparedStatement ps1 = conn.prepareStatement(sql1)) {
                ps1.setDouble(1, monto);
                ps1.setInt(2, idFactura);
                ps1.executeUpdate();
            }

            String sql2 = "INSERT INTO movimiento_caja (fecha, monto, descripcion, id_categoria, id_usuario) VALUES (NOW(), ?, CONCAT('Cobro Factura #', ?), 1, ?)";
            try (PreparedStatement ps2 = conn.prepareStatement(sql2)) {
                ps2.setDouble(1, monto);
                ps2.setInt(2, idFactura);
                ps2.setInt(3, idUsuario);
                ps2.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (Exception e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (Exception ex) {
            }
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

    // 3. ADELANTAR MES (Generación Inteligente de Recibo)
    // CORREGIDO: Solo genera si la próxima factura está dentro del rango válido
    public boolean generarSiguienteFactura(int idSuscripcion) {
        Connection conn = null;
        try {
            conn = Conexion.getConexion();

            // A. Obtener datos del contrato (Mes Adelantado?, Costo Plan?, Día Pago?)
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

            // B. Calcular la fecha de la PRÓXIMA factura
            LocalDate hoy = LocalDate.now();
            LocalDate fechaProximaFactura;

            // Buscar la última factura generada
            String sqlUltima = "SELECT fecha_vencimiento FROM factura WHERE id_suscripcion = ? ORDER BY fecha_vencimiento DESC LIMIT 1";
            LocalDate ultimaFechaVencimiento = null;

            try (PreparedStatement ps = conn.prepareStatement(sqlUltima)) {
                ps.setInt(1, idSuscripcion);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Date fechaSql = rs.getDate(1);
                    if (fechaSql != null) {
                        ultimaFechaVencimiento = fechaSql.toLocalDate();
                    }
                }
            }

            if (ultimaFechaVencimiento == null) {
                // PRIMERA factura: usar el día de pago del mes actual/siguiente
                if (esMesAdelantado) {
                    // Prepago: genera para el próximo mes
                    fechaProximaFactura = hoy.plusMonths(1)
                            .withDayOfMonth(Math.min(diaPago, hoy.plusMonths(1).lengthOfMonth()));
                } else {
                    // Postpago: genera para el mes actual
                    fechaProximaFactura = hoy.withDayOfMonth(Math.min(diaPago, hoy.lengthOfMonth()));
                }
            } else {
                // Ya existe factura previa: la nueva es 1 mes después
                fechaProximaFactura = ultimaFechaVencimiento.plusMonths(1);
            }

            // C. VALIDACIÓN CRÍTICA #1: Solo generar si ya llegó o pasó el día de pago
            // Si hoy es 24 y dia_pago es 28, NO generar aún
            int diaHoy = hoy.getDayOfMonth();

            // Para PREPAGO: la factura del próximo mes se genera en el día de pago del mes
            // actual
            // Para POSTPAGO: la factura del mes actual se genera en el día de pago del mes
            // actual
            if (ultimaFechaVencimiento != null) {
                // Ya tiene facturas previas - verificar si es hora de generar la siguiente
                java.time.YearMonth mesUltimaFactura = java.time.YearMonth.from(ultimaFechaVencimiento);
                java.time.YearMonth mesActual = java.time.YearMonth.from(hoy);

                // Si la última factura es del mes actual o futuro, no generar
                if (!mesUltimaFactura.isBefore(mesActual)) {
                    // Ya tiene factura para este mes o después
                    if (esMesAdelantado) {
                        // Prepago: ya tiene factura del próximo mes, no generar más
                        java.time.YearMonth mesSiguiente = mesActual.plusMonths(1);
                        if (!mesUltimaFactura.isBefore(mesSiguiente)) {
                            System.out.println(
                                    "   ℹ️ Suscripción " + idSuscripcion + " ya tiene factura del mes siguiente");
                            return false;
                        }
                    } else {
                        System.out.println("   ℹ️ Suscripción " + idSuscripcion + " ya tiene factura del mes actual");
                        return false;
                    }
                }

                // Verificar si ya pasó el día de pago para generar la siguiente
                if (diaHoy < diaPago) {
                    System.out.println("   ⏳ Suscripción " + idSuscripcion + " - día de pago es " + diaPago
                            + ", hoy es " + diaHoy + ". Esperar.");
                    return false;
                }
            }

            // D. VALIDACIÓN CRÍTICA #2: Solo generar si corresponde al MES SIGUIENTE o
            // actual
            java.time.YearMonth mesActualFinal = java.time.YearMonth.from(hoy);
            java.time.YearMonth mesFactura = java.time.YearMonth.from(fechaProximaFactura);
            java.time.YearMonth mesLimite = mesActualFinal.plusMonths(1);

            if (mesFactura.isAfter(mesLimite)) {
                System.out.println("   ℹ️ Factura para " + idSuscripcion + " ya está al día (próxima: "
                        + fechaProximaFactura + ", límite: " + mesLimite + ")");
                return false; // No generar, es para más de 1 mes en el futuro
            }

            // D. Verificar que no exista ya una factura PENDIENTE para este periodo
            String sqlExiste = "SELECT COUNT(*) FROM factura WHERE id_suscripcion = ? AND id_estado = 1 " +
                    "AND YEAR(fecha_vencimiento) = ? AND MONTH(fecha_vencimiento) = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlExiste)) {
                ps.setInt(1, idSuscripcion);
                ps.setInt(2, fechaProximaFactura.getYear());
                ps.setInt(3, fechaProximaFactura.getMonthValue());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println("   ℹ️ Ya existe factura pendiente para " + fechaProximaFactura.getMonth() + " "
                            + fechaProximaFactura.getYear());
                    return false; // Ya existe
                }
            }

            // E. Construir el TEXTO DEL PERIODO
            DateTimeFormatter fmtMes = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES"));
            String nombrePeriodo;

            if (esMesAdelantado) {
                // Prepago: Cobra el mes de la fecha de vencimiento
                String mesNombre = fechaProximaFactura.format(fmtMes);
                nombrePeriodo = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1).toLowerCase();
            } else {
                // Postpago: Cobra el mes anterior a la fecha de vencimiento
                LocalDate mesACobrar = fechaProximaFactura.minusMonths(1);
                String mesNombre = mesACobrar.format(fmtMes);
                nombrePeriodo = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1).toLowerCase();
            }

            // F. Insertar Factura
            String sqlInsert = "INSERT INTO factura (id_suscripcion, fecha_emision, fecha_vencimiento, monto_total, monto_pagado, id_estado, codigo_factura, periodo_mes) "
                    +
                    "VALUES (?, NOW(), ?, ?, 0.00, 1, CONCAT('F-', FLOOR(RAND()*100000)), ?)";

            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setInt(1, idSuscripcion);
                ps.setDate(2, java.sql.Date.valueOf(fechaProximaFactura));
                ps.setDouble(3, montoMensual);
                ps.setString(4, nombrePeriodo);

                boolean insertado = ps.executeUpdate() > 0;
                if (insertado) {
                    System.out.println(
                            "   ✅ Factura generada: " + nombrePeriodo + " (vence: " + fechaProximaFactura + ")");
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
        String sql = "SELECT periodo_mes, fecha_vencimiento, monto_total, monto_pagado, fecha_pago, id_estado " +
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
                        rs.getDate("fecha_pago") // Puede ser null
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
        String sql = "SELECT id_factura, periodo_mes, fecha_vencimiento, monto_total, monto_pagado, fecha_pago, id_estado "
                +
                "FROM factura WHERE id_suscripcion = ? ORDER BY fecha_vencimiento DESC";

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
                        rs.getDate("fecha_vencimiento"), // 2: Vencimiento
                        rs.getDouble("monto_total"), // 3: Monto
                        estado, // 4: Estado texto
                        rs.getDate("fecha_pago"), // 5: Fecha pago (puede ser null)
                        idEstado // 6: ID Estado numérico
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
     * Crea una factura manual (para migración de datos desde Excel).
     * 
     * @param registrarEnCaja Si es true y estado=PAGADO, registra en
     *                        movimiento_caja
     */
    public boolean crearFacturaManual(int idSuscripcion, String periodoMes, double monto,
            int estado, java.sql.Date fechaVencimiento,
            boolean registrarEnCaja, int idUsuario) {
        Connection conn = null;
        try {
            conn = Conexion.getConexion();
            conn.setAutoCommit(false);

            String sqlInsert = "INSERT INTO factura (id_suscripcion, fecha_emision, fecha_vencimiento, " +
                    "monto_total, monto_pagado, id_estado, codigo_factura, periodo_mes, fecha_pago) " +
                    "VALUES (?, NOW(), ?, ?, ?, ?, CONCAT('MIG-', FLOOR(RAND()*100000)), ?, ?)";

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
                    psCaja.setString(3, "Migración - " + periodoMes + " (Factura #" + idFacturaGenerada + ")");
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
}