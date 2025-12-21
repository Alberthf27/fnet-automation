package DAO;

import bd.Conexion;
import modelo.AlertaGerente;
import modelo.AlertaGerente.TipoAlerta;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gestionar alertas de la bandeja de entrada del gerente.
 */
public class AlertaDAO {

    /**
     * Crea una nueva alerta para el gerente.
     */
    public boolean crearAlerta(AlertaGerente a) {
        String sql = "INSERT INTO alerta_gerente " +
                "(tipo, titulo, mensaje, id_suscripcion, leido) " +
                "VALUES (?, ?, ?, ?, 0)";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getTipo().name());
            ps.setString(2, a.getTitulo());
            ps.setString(3, a.getMensaje());
            if (a.getIdSuscripcion() != null) {
                ps.setInt(4, a.getIdSuscripcion());
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Crea una alerta de cliente sin teléfono.
     */
    public boolean crearAlertaSinTelefono(int idSuscripcion, String nombreCliente, String codigoContrato) {
        AlertaGerente alerta = new AlertaGerente(
                TipoAlerta.SIN_TELEFONO,
                "Cliente sin número de WhatsApp",
                String.format("El cliente '%s' (Contrato: %s) no tiene número de teléfono registrado. " +
                        "No se puede enviar notificación de cobro.", nombreCliente, codigoContrato));
        alerta.setIdSuscripcion(idSuscripcion);
        return crearAlerta(alerta);
    }

    /**
     * Crea una alerta de corte fallido.
     */
    public boolean crearAlertaCorteFallido(int idSuscripcion, String nombreCliente, String error) {
        AlertaGerente alerta = new AlertaGerente(
                TipoAlerta.CORTE_FALLIDO,
                "Error al cortar servicio",
                String.format("No se pudo cortar el servicio del cliente '%s'. Error: %s. " +
                        "Requiere intervención manual.", nombreCliente, error));
        alerta.setIdSuscripcion(idSuscripcion);
        return crearAlerta(alerta);
    }

    /**
     * Obtiene todas las alertas no leídas.
     */
    public List<AlertaGerente> obtenerNoLeidas() {
        List<AlertaGerente> lista = new ArrayList<>();
        String sql = "SELECT a.*, " +
                "COALESCE(CONCAT(c.nombres, ' ', c.apellidos), '') as nombre_cliente, " +
                "COALESCE(s.codigo_contrato, '') as codigo_contrato " +
                "FROM alerta_gerente a " +
                "LEFT JOIN suscripcion s ON a.id_suscripcion = s.id_suscripcion " +
                "LEFT JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE a.leido = 0 " +
                "ORDER BY a.fecha_creacion DESC";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearAlerta(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Obtiene todas las alertas (para historial).
     */
    public List<AlertaGerente> obtenerTodas(int limite) {
        List<AlertaGerente> lista = new ArrayList<>();
        String sql = "SELECT a.*, " +
                "COALESCE(CONCAT(c.nombres, ' ', c.apellidos), '') as nombre_cliente, " +
                "COALESCE(s.codigo_contrato, '') as codigo_contrato " +
                "FROM alerta_gerente a " +
                "LEFT JOIN suscripcion s ON a.id_suscripcion = s.id_suscripcion " +
                "LEFT JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "ORDER BY a.fecha_creacion DESC LIMIT ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(mapearAlerta(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Cuenta las alertas no leídas (para badge).
     */
    public int contarNoLeidas() {
        String sql = "SELECT COUNT(*) FROM alerta_gerente WHERE leido = 0";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Marca una alerta como leída.
     */
    public boolean marcarComoLeida(int idAlerta) {
        String sql = "UPDATE alerta_gerente SET leido = 1 WHERE id_alerta = ?";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idAlerta);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Marca todas las alertas como leídas.
     */
    public boolean marcarTodasComoLeidas() {
        String sql = "UPDATE alerta_gerente SET leido = 1 WHERE leido = 0";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Elimina alertas antiguas (más de 30 días y ya leídas).
     */
    public int limpiarAlertas() {
        String sql = "DELETE FROM alerta_gerente " +
                "WHERE leido = 1 AND fecha_creacion < DATE_SUB(NOW(), INTERVAL 30 DAY)";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Mapea un ResultSet a AlertaGerente.
     */
    private AlertaGerente mapearAlerta(ResultSet rs) throws SQLException {
        AlertaGerente a = new AlertaGerente();
        a.setIdAlerta(rs.getInt("id_alerta"));
        a.setTipo(rs.getString("tipo"));
        a.setTitulo(rs.getString("titulo"));
        a.setMensaje(rs.getString("mensaje"));

        int idSusc = rs.getInt("id_suscripcion");
        a.setIdSuscripcion(rs.wasNull() ? null : idSusc);

        a.setLeido(rs.getInt("leido") == 1);
        a.setFechaCreacion(rs.getTimestamp("fecha_creacion"));

        // Campos opcionales
        try {
            a.setNombreCliente(rs.getString("nombre_cliente"));
            a.setCodigoContrato(rs.getString("codigo_contrato"));
        } catch (SQLException ignored) {
        }

        return a;
    }
}
