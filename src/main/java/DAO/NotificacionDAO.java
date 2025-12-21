package DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import bd.Conexion;
import modelo.NotificacionPendiente;
import modelo.NotificacionPendiente.TipoNotificacion;

/**
 * DAO para gestionar notificaciones de WhatsApp pendientes.
 */
public class NotificacionDAO {

    /**
     * Crea una nueva notificación pendiente.
     */
    public boolean crearNotificacion(NotificacionPendiente n) {
        String sql = "INSERT INTO notificacion_pendiente " +
                "(id_suscripcion, tipo, mensaje, telefono, fecha_programada, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, n.getIdSuscripcion());
            ps.setString(2, n.getTipo().name());
            ps.setString(3, n.getMensaje());
            ps.setString(4, n.getTelefono());
            ps.setDate(5, n.getFechaProgramada());
            ps.setString(6, n.getEstado().name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtiene todas las notificaciones pendientes de envío.
     * Solo retorna las que tienen fecha_programada <= hoy.
     */
    public List<NotificacionPendiente> obtenerPendientes() {
        List<NotificacionPendiente> lista = new ArrayList<>();
        String sql = "SELECT n.*, " +
                "CONCAT(c.nombres, ' ', c.apellidos) as nombre_cliente, " +
                "s.codigo_contrato " +
                "FROM notificacion_pendiente n " +
                "JOIN suscripcion s ON n.id_suscripcion = s.id_suscripcion " +
                "JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE n.estado = 'PENDIENTE' AND n.fecha_programada <= CURRENT_DATE() " +
                "ORDER BY n.fecha_programada ASC";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearNotificacion(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Obtiene las notificaciones fallidas por falta de número
     * para mostrarlas en la "Bandeja del Gerente".
     */
    public List<NotificacionPendiente> obtenerErroresSinTelefono() {
        List<NotificacionPendiente> lista = new ArrayList<>();
        String sql = "SELECT n.*, CONCAT(c.nombres, ' ', c.apellidos) as nombre_cliente, s.codigo_contrato " +
                "FROM notificacion_pendiente n " +
                "JOIN suscripcion s ON n.id_suscripcion = s.id_suscripcion " +
                "JOIN cliente c ON s.id_cliente = c.id_cliente " +
                "WHERE n.estado = 'SIN_TELEFONO' " +
                "ORDER BY n.fecha_programada DESC";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearNotificacion(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Obtiene notificaciones por suscripción.
     */
    public List<NotificacionPendiente> obtenerPorSuscripcion(int idSuscripcion) {
        List<NotificacionPendiente> lista = new ArrayList<>();
        String sql = "SELECT * FROM notificacion_pendiente " +
                "WHERE id_suscripcion = ? ORDER BY fecha_programada DESC";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(mapearNotificacion(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Marca una notificación como enviada.
     */
    public boolean marcarComoEnviada(int idNotificacion) {
        String sql = "UPDATE notificacion_pendiente " +
                "SET estado = 'ENVIADO', fecha_enviado = NOW() " +
                "WHERE id_notificacion = ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idNotificacion);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Marca una notificación como error (no se pudo enviar).
     */
    public boolean marcarComoError(int idNotificacion) {
        String sql = "UPDATE notificacion_pendiente SET estado = 'ERROR' WHERE id_notificacion = ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idNotificacion);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Marca como SIN_TELEFONO (para crear alerta al gerente).
     */
    public boolean marcarSinTelefono(int idNotificacion) {
        String sql = "UPDATE notificacion_pendiente SET estado = 'SIN_TELEFONO' WHERE id_notificacion = ?";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idNotificacion);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cancela notificaciones pendientes de una suscripción.
     * Usado cuando el cliente paga antes del envío.
     */
    public boolean cancelarPendientes(int idSuscripcion) {
        String sql = "DELETE FROM notificacion_pendiente " +
                "WHERE id_suscripcion = ? AND estado = 'PENDIENTE'";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifica si ya existe una notificación del mismo tipo pendiente.
     */
    public boolean existeNotificacionPendiente(int idSuscripcion, TipoNotificacion tipo) {
        String sql = "SELECT 1 FROM notificacion_pendiente " +
                "WHERE id_suscripcion = ? AND tipo = ? AND estado = 'PENDIENTE'";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSuscripcion);
            ps.setString(2, tipo.name());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Mapea un ResultSet a NotificacionPendiente.
     */
    private NotificacionPendiente mapearNotificacion(ResultSet rs) throws SQLException {
        NotificacionPendiente n = new NotificacionPendiente();
        n.setIdNotificacion(rs.getInt("id_notificacion"));
        n.setIdSuscripcion(rs.getInt("id_suscripcion"));
        n.setTipo(rs.getString("tipo"));
        n.setMensaje(rs.getString("mensaje"));
        n.setTelefono(rs.getString("telefono"));
        n.setFechaProgramada(rs.getDate("fecha_programada"));
        n.setFechaEnviado(rs.getTimestamp("fecha_enviado"));
        n.setEstado(rs.getString("estado"));

        // Campos opcionales (pueden no estar en todas las consultas)
        try {
            n.setNombreCliente(rs.getString("nombre_cliente"));
            n.setCodigoContrato(rs.getString("codigo_contrato"));
        } catch (SQLException ignored) {
        }

        return n;
    }
}
