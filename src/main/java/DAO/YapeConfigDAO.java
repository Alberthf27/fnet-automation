package DAO;

import bd.Conexion;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DAO para gestionar la configuración de Yape.
 * Guarda la última fecha procesada para evitar duplicados.
 */
public class YapeConfigDAO {

    /**
     * Obtiene la última fecha de transacción procesada.
     * 
     * @return Fecha de la última transacción procesada, o null si es la primera vez
     */
    public Date obtenerUltimaFechaProcesada() {
        String sql = "SELECT valor FROM yape_config WHERE clave = 'ultima_fecha_procesada'";

        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String valorFecha = rs.getString("valor");
                if (valorFecha != null && !valorFecha.isEmpty()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return sdf.parse(valorFecha);
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo última fecha procesada: " + e.getMessage());
        }

        return null; // Primera vez
    }

    /**
     * Actualiza la última fecha de transacción procesada.
     * 
     * @param fecha Nueva fecha a guardar
     * @return true si se actualizó correctamente
     */
    public boolean actualizarUltimaFechaProcesada(Date fecha) {
        String sql = "UPDATE yape_config SET valor = ? WHERE clave = 'ultima_fecha_procesada'";

        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            ps.setString(1, sdf.format(fecha));

            int affected = ps.executeUpdate();

            if (affected > 0) {
                System.out.println("✅ Última fecha procesada actualizada: " + sdf.format(fecha));
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error actualizando última fecha: " + e.getMessage());
        }

        return false;
    }

    /**
     * Reinicia la configuración (útil para testing).
     */
    public void reiniciarConfiguracion() {
        String sql = "UPDATE yape_config SET valor = NULL WHERE clave = 'ultima_fecha_procesada'";

        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
            System.out.println("⚠️ Configuración Yape reiniciada");
        } catch (SQLException e) {
            System.err.println("Error reiniciando configuración: " + e.getMessage());
        }
    }
}
