package bd;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Conexión a base de datos para el servicio de automatización.
 * 
 * A partir de v2.2, delega en PoolConexiones (HikariCP) para
 * evitar memory leaks por creación excesiva de conexiones.
 * 
 * Uso: igual que antes — Conexion.getConexion() en try-with-resources.
 * La conexión se devuelve automáticamente al pool al cerrarse.
 */
public class Conexion {

    public Connection cadena;

    public Conexion() {
        this.cadena = null;
    }

    /**
     * Conecta a la BD usando el pool de conexiones.
     * Mantenido por compatibilidad, pero delega en el pool.
     */
    public Connection conectar() {
        try {
            this.cadena = PoolConexiones.getConexion();
            return this.cadena;
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener conexión del pool: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cierra la conexión (la devuelve al pool).
     */
    public void desconectar() {
        try {
            if (this.cadena != null && !this.cadena.isClosed()) {
                this.cadena.close();
            }
        } catch (SQLException e) {
            // Ignorar errores al cerrar
        } finally {
            this.cadena = null;
        }
    }

    /**
     * Obtiene una conexión directamente del pool.
     * Este es el método recomendado para usar con try-with-resources.
     * Reemplaza al antiguo que creaba una conexión nueva cada vez.
     */
    public static Connection getConexion() {
        try {
            return PoolConexiones.getConexion();
        } catch (SQLException e) {
            System.err.println("❌ Error crítico al obtener conexión del pool: " + e.getMessage());
            return null;
        }
    }
}
