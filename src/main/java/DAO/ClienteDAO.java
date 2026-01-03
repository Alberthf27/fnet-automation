package DAO;

import bd.Conexion;
import modelo.Cliente;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAO {

    public List<Cliente> obtenerClientesPaginados(int limit, int offset) {
        List<Cliente> clientes = new ArrayList<>();
        // ✅ Corregido: Uso de minúsculas y guiones bajos en SQL
        String sql = "SELECT id_cliente, dni_cliente, nombres, apellidos, direccion, correo, fecha_registro, activo, deuda "
                + "FROM cliente "
                + "WHERE activo = 1 "
                + "ORDER BY apellidos, nombres "
                + "LIMIT ? OFFSET ?";

        // Uso del try-with-resources para el manejo automático de la conexión y
        // recursos (Mejor práctica en Java)
        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapearCliente(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en obtenerClientesPaginados: " + e.getMessage());
            e.printStackTrace();
        }
        return clientes;
    }

    // AGREGAR ESTO EN TU CLASE ClienteDAO

    public List<Cliente> listarTodo(String busqueda, String orden) {
        List<Cliente> lista = new ArrayList<>();
        String sql = "SELECT * FROM cliente WHERE (nombres LIKE ? OR apellidos LIKE ? OR dni_cliente LIKE ?)";

        // Lógica de Filtros
        if (orden != null) {
            switch (orden) {
                case "NOMBRE (A-Z)":
                    sql += " ORDER BY nombres ASC";
                    break;
                case "APELLIDO (A-Z)":
                    sql += " ORDER BY apellidos ASC";
                    break;
                case "SOLO ACTIVOS":
                    sql += " AND activo = 1 ORDER BY nombres ASC";
                    break;
                case "SOLO BAJAS":
                    sql += " AND activo = 0 ORDER BY nombres ASC";
                    break;
                default:
                    sql += " ORDER BY id_cliente DESC";
                    break;
            }
        } else {
            sql += " ORDER BY id_cliente DESC";
        }

        try (java.sql.Connection conn = bd.Conexion.getConexion();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            String pattern = "%" + busqueda + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cliente c = new Cliente();
                    c.setIdCliente(rs.getLong("id_cliente"));
                    c.setNombres(rs.getString("nombres"));
                    c.setApellidos(rs.getString("apellidos"));
                    c.setDniCliente(rs.getString("dni_cliente"));
                    c.setDireccion(rs.getString("direccion"));
                    c.setTelefono(rs.getString("telefono"));
                    c.setActivo(rs.getInt("activo"));
                    // Agrega otros campos si tu modelo Cliente tiene más (email, coordenadas, etc.)
                    lista.add(c);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    public List<Cliente> obtenerClientesPaginados(int limite, int offset, String criterio) {
        List<Cliente> lista = new ArrayList<>();
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        // 1. Construimos la consulta base
        String sql = "SELECT * FROM cliente ";

        // 2. Modificamos la consulta según lo que seleccionó en el ComboBox
        if (criterio != null) {
            switch (criterio) {
                case "NOMBRE (A-Z)":
                    sql += "ORDER BY nombres ASC ";
                    break;
                case "APELLIDO (A-Z)":
                    sql += "ORDER BY apellidos ASC ";
                    break;
                case "SOLO ACTIVOS":
                    sql += "WHERE activo = 1 ORDER BY nombres ASC ";
                    break;
                case "SOLO BAJAS":
                    sql += "WHERE activo = 0 ORDER BY nombres ASC ";
                    break;
                default:
                    sql += "ORDER BY id_cliente DESC "; // Orden por defecto (más nuevos primero)
                    break;
            }
        } else {
            sql += "ORDER BY id_cliente DESC ";
        }

        // 3. Agregamos la paginación al final
        sql += "LIMIT ? OFFSET ?";

        try {
            con = Conexion.getConexion(); // O como obtengas tu conexión
            ps = con.prepareStatement(sql);
            ps.setInt(1, limite);
            ps.setInt(2, offset);

            rs = ps.executeQuery();

            while (rs.next()) {
                Cliente c = new Cliente();
                // Cambia esto:
                // c.setIdCliente(rs.getInt("id_cliente"));

                // Por esto:
                c.setIdCliente(rs.getLong("id_cliente"));
                c.setDniCliente(rs.getString("dni_cliente"));
                c.setNombres(rs.getString("nombres"));
                c.setApellidos(rs.getString("apellidos"));
                c.setDireccion(rs.getString("direccion"));
                c.setTelefono(rs.getString("telefono"));
                c.setActivo(rs.getInt("activo"));
                // Agrega los campos que falten según tu tabla
                lista.add(c);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Cierra tus recursos aquí (rs, ps, con)
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
            }
        }

        return lista;
    }

    /**
     * Registra un cliente y retorna su ID generado.
     * Retorna -1 si hubo error.
     * DNI puede ser null o vacío (se guardará como NULL en BD).
     */
    public int registrarClienteYObtenerId(String dni, String nom, String ape, String dir, String tel) {
        String sql = "INSERT INTO cliente (dni_cliente, nombres, apellidos, direccion, telefono, activo) VALUES (?, ?, ?, ?, ?, 1)";

        try (java.sql.Connection conn = bd.Conexion.getConexion();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            // DNI: si está vacío, guardar como NULL para evitar duplicados
            if (dni == null || dni.trim().isEmpty()) {
                ps.setNull(1, java.sql.Types.VARCHAR);
            } else {
                ps.setString(1, dni.trim());
            }
            ps.setString(2, nom);
            ps.setString(3, ape);
            ps.setString(4, dir); // Usaremos la misma dirección de instalación por defecto
            ps.setString(5, tel);

            int affected = ps.executeUpdate();

            if (affected > 0) {
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1); // Retorna el ID nuevo
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error registrando cliente express: " + e.getMessage());
        }
        return -1;
    }

    // Método optimizado para llenar la lista de autocompletado
    public List<String[]> obtenerListaSimpleClientes() {
        List<String[]> lista = new ArrayList<>();
        // Traemos solo lo necesario para buscar
        String sql = "SELECT dni_cliente, nombres, apellidos FROM cliente WHERE activo = 1";

        try (java.sql.Connection conn = bd.Conexion.getConexion();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql);
                java.sql.ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Guardamos: [0]=DNI, [1]=Nombre Completo
                lista.add(new String[] {
                        rs.getString("dni_cliente"),
                        rs.getString("nombres") + " " + rs.getString("apellidos")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Busca clientes para dropdown con ID, nombres, apellidos y teléfono.
     * Retorna array: [0]=ID, [1]=Nombres, [2]=Apellidos, [3]=Teléfono, [4]=DNI
     */
    public List<Object[]> buscarClientesParaDropdown(String criterio) {
        List<Object[]> lista = new ArrayList<>();
        String sql = "SELECT id_cliente, nombres, apellidos, telefono, dni_cliente " +
                "FROM cliente WHERE activo = 1 " +
                "AND (nombres LIKE ? OR apellidos LIKE ? OR dni_cliente LIKE ?) " +
                "ORDER BY apellidos, nombres LIMIT 20";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            String pattern = "%" + (criterio != null ? criterio : "") + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Object[] {
                            rs.getInt("id_cliente"), // 0: ID
                            rs.getString("nombres"), // 1: Nombres
                            rs.getString("apellidos"), // 2: Apellidos
                            rs.getString("telefono"), // 3: Teléfono
                            rs.getString("dni_cliente") // 4: DNI
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("Error buscando clientes para dropdown: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Obtiene el número total de clientes activos. Es necesario para calcular
     * el número total de páginas.
     */
    public int obtenerTotalClientesActivos() {
        String sql = "SELECT COUNT(id_cliente) FROM cliente WHERE activo = 1";
        try (Connection conn = Conexion.getConexion();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener total de clientes: " + e.getMessage());
        }
        return 0;
    }

    /**
     * ✅ MEJORADO: Búsqueda de clientes (Activo=1) Utiliza los índices creados
     * (dni_cliente, apellidos, nombres)
     */
    public List<Cliente> buscarClientes(String criterio) {
        List<Cliente> clientes = new ArrayList<>();
        // ✅ Corregido: Uso de minúsculas y guiones bajos en SQL
        String sql = "SELECT * FROM cliente WHERE (nombres LIKE ? OR apellidos LIKE ? OR dni_cliente LIKE ?) AND activo = 1 ORDER BY apellidos, nombres";

        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            String likeCriterio = "%" + criterio + "%";
            stmt.setString(1, likeCriterio);
            stmt.setString(2, likeCriterio);
            stmt.setString(3, likeCriterio);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapearCliente(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al buscar clientes: " + e.getMessage());
            e.printStackTrace();
        }
        return clientes;
    }

    // ... dentro de ClienteDAO ...
    /**
     * Registra Cliente y (Opcional) su primer Contrato en una sola transacción
     * segura.
     */
    public boolean registrarClienteCompleto(Cliente c, int idServicio, boolean incluirContrato) {
        Connection conn = null;
        PreparedStatement psCliente = null;
        PreparedStatement psContrato = null;
        boolean exito = false;

        try {
            conn = Conexion.getConexion();
            conn.setAutoCommit(false); // 🛑 INICIO TRANSACCIÓN (Nada se guarda real hasta el commit)

            // 1. INSERTAR CLIENTE
            String sqlC = "INSERT INTO cliente (dni_cliente, nombres, apellidos, direccion, correo, telefono, fecha_registro, activo, deuda) "
                    + "VALUES (?, ?, ?, ?, ?, ?, NOW(), 1, 0.0)";

            psCliente = conn.prepareStatement(sqlC, Statement.RETURN_GENERATED_KEYS);
            psCliente.setString(1, c.getDniCliente());
            psCliente.setString(2, c.getNombres());
            psCliente.setString(3, c.getApellidos());
            psCliente.setString(4, c.getDireccion());
            psCliente.setString(5, c.getCorreo());
            psCliente.setString(6, c.getTelefono()); // Asegúrate de tener este campo en Cliente.java

            int rows = psCliente.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Fallo al crear cliente");
            }

            // Obtener ID generado
            long idClienteGenerado = 0;
            try (ResultSet rs = psCliente.getGeneratedKeys()) {
                if (rs.next()) {
                    idClienteGenerado = rs.getLong(1);
                }
            }

            // 2. INSERTAR CONTRATO (Si se seleccionó la opción)
            if (incluirContrato && idClienteGenerado > 0) {
                // Ajusta nombres de columnas según tu tabla 'suscripcion'
                String sqlS = "INSERT INTO suscripcion (id_cliente, id_servicio, fecha_inicio, activo, direccion_instalacion) "
                        + "VALUES (?, ?, NOW(), 1, ?)";

                psContrato = conn.prepareStatement(sqlS);
                psContrato.setLong(1, idClienteGenerado);
                psContrato.setInt(2, idServicio);
                psContrato.setString(3, c.getDireccion()); // Asumimos misma dirección

                psContrato.executeUpdate();
            }

            conn.commit(); // ✅ CONFIRMAR CAMBIOS
            exito = true;
            System.out.println("Transacción exitosa: Cliente " + idClienteGenerado);

        } catch (Exception e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
            } // ↩️ DESHACER SI FALLA
            System.err.println("Error transacción cliente: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (psCliente != null) {
                    psCliente.close();
                }
                if (psContrato != null) {
                    psContrato.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
            }
        }
        return exito;
    }

    // ----------------------------------------------------------------------------------
    // ⚙️ MÉTODOS CRUD (Corregidos y con Try-with-resources)
    // ----------------------------------------------------------------------------------
    public Long insertarCliente(Cliente cliente) {
        Long generatedId = null;
        // ✅ Corregido: Uso de minúsculas y la función NOW()
        String sql = "INSERT INTO cliente (dni_cliente, nombres, apellidos, direccion, correo, fecha_registro, activo, deuda) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), 1, ?)";

        try (Connection conn = Conexion.getConexion();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, cliente.getDniCliente());
            stmt.setString(2, cliente.getNombres());
            stmt.setString(3, cliente.getApellidos());
            stmt.setString(4, cliente.getDireccion());
            stmt.setString(5, cliente.getCorreo());
            stmt.setDouble(6, cliente.getDeuda() != null ? cliente.getDeuda() : 0.0);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        generatedId = generatedKeys.getLong(1);
                        System.out.println("Cliente insertado con ID: " + generatedId);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al insertar cliente: " + e.getMessage());
            e.printStackTrace();
        }
        return generatedId;
    }

    public boolean actualizarCliente(Cliente cliente) {
        // ✅ Corregido: Uso de minúsculas y guiones bajos en SQL
        String sql = "UPDATE cliente SET dni_cliente = ?, nombres = ?, apellidos = ?, direccion = ?, correo = ? WHERE id_cliente = ?";
        boolean resultado = false;

        if (cliente == null || cliente.getIdCliente() == null) {
            return false;
        }

        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cliente.getDniCliente());
            stmt.setString(2, cliente.getNombres());
            stmt.setString(3, cliente.getApellidos());
            stmt.setString(4, cliente.getDireccion());
            stmt.setString(5, cliente.getCorreo());
            stmt.setLong(6, cliente.getIdCliente());

            resultado = stmt.executeUpdate() > 0;
            if (resultado) {
                System.out.println("Cliente actualizado - ID: " + cliente.getIdCliente());
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al actualizar cliente: " + e.getMessage());
            e.printStackTrace();
        }
        return resultado;
    }

    public boolean eliminarCliente(Long idCliente) {
        // En tu BD, eliminar es poner ACTIVO = 0
        String sql = "UPDATE cliente SET activo = 0 WHERE id_cliente = ?";
        boolean resultado = false;

        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idCliente);
            resultado = stmt.executeUpdate() > 0;

            if (resultado) {
                System.out.println("Cliente eliminado (ACTIVO=0) - ID: " + idCliente);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al eliminar (desactivar) cliente: " + e.getMessage());
            e.printStackTrace();
        }
        return resultado;
    }

    public boolean actualizarDeuda(Long idCliente, Double nuevaDeuda) {
        String sql = "UPDATE cliente SET deuda = ? WHERE id_cliente = ?";
        boolean resultado = false;

        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, nuevaDeuda);
            stmt.setLong(2, idCliente);
            resultado = stmt.executeUpdate() > 0;

            if (resultado) {
                System.out.println("Deuda actualizada correctamente para cliente ID: " + idCliente);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al actualizar deuda: " + e.getMessage());
            e.printStackTrace();
        }
        return resultado;
    }

    public boolean agregarDeuda(Long idCliente, Double montoAAgregar) {
        String sql = "UPDATE cliente SET deuda = deuda + ? WHERE id_cliente = ?";
        boolean resultado = false;

        if (montoAAgregar == null || montoAAgregar <= 0) {
            return false;
        }

        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, montoAAgregar);
            stmt.setLong(2, idCliente);
            resultado = stmt.executeUpdate() > 0;

            if (resultado) {
                System.out.println("Se agregó " + montoAAgregar + " a la deuda del cliente ID: " + idCliente);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al agregar deuda: " + e.getMessage());
            e.printStackTrace();
        }
        return resultado;
    }

    // ----------------------------------------------------------------------------------
    // 🧰 HELPER (Ayudante)
    // ----------------------------------------------------------------------------------
    /**
     * Mapea un ResultSet a un objeto Cliente (Evita repetir código)
     */
    private Cliente mapearCliente(ResultSet rs) throws SQLException {
        Cliente cliente = new Cliente();
        // ✅ Uso de nombres de columna exactos: id_cliente, dni_cliente, fecha_registro,
        // activo, deuda
        cliente.setIdCliente(rs.getLong("id_cliente"));
        cliente.setDniCliente(rs.getString("dni_cliente"));
        cliente.setNombres(rs.getString("nombres"));
        cliente.setApellidos(rs.getString("apellidos"));
        cliente.setDireccion(rs.getString("direccion"));
        cliente.setCorreo(rs.getString("correo"));
        cliente.setFechaRegistro(rs.getTimestamp("fecha_registro")); // Es datetime, mejor usar Timestamp o Date
        cliente.setActivo(rs.getInt("activo"));
        cliente.setDeuda(rs.getDouble("deuda"));
        return cliente;
    }

    /**
     * NOTA: Este método original ha sido reemplazado por
     * obtenerClientesPaginados() para mejorar el rendimiento. Se mantiene
     * comentado si necesitas el patrón antiguo.
     */
    /*
     * public List<Cliente> obtenerTodosClientes() {
     * return obtenerClientesPaginados(1000000, 0); // Limitarlo a un número
     * gigante, pero forzar el LIMIT/OFFSET
     * }
     */

    /**
     * Busca un cliente por DNI y retorna sus datos en un Map.
     * Usado para procesamiento automático de pagos Yape.
     * 
     * @param dni DNI del cliente a buscar
     * @return Map con datos del cliente o null si no existe
     */
    public java.util.Map<String, Object> buscarPorDNI(String dni) {
        String sql = "SELECT id_cliente, dni_cliente, nombres, apellidos, telefono FROM cliente " +
                "WHERE dni_cliente = ? AND activo = 1";

        try (Connection con = Conexion.getConexion();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, dni);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                java.util.Map<String, Object> cliente = new java.util.HashMap<>();
                cliente.put("id_cliente", rs.getInt("id_cliente"));
                cliente.put("dni_cliente", rs.getString("dni_cliente"));
                cliente.put("nombres", rs.getString("nombres"));
                cliente.put("apellidos", rs.getString("apellidos"));
                cliente.put("telefono", rs.getString("telefono"));
                return cliente;
            }
        } catch (SQLException e) {
            System.err.println("Error buscando cliente por DNI: " + e.getMessage());
        }

        return null;
    }
}
