package modelo;

import java.sql.Timestamp;

public class Cliente {
    private Long idCliente;
    private String dniCliente;
    private String nombres;
    private String apellidos;
    private String direccion;
    private String correo;
    private String telefono; // <--- ESTE FALTABA
    private Timestamp fechaRegistro;
    private int activo;
    private Double deuda;

    public Cliente() {}

    // Getters y Setters
    public Long getIdCliente() { return idCliente; }
    public void setIdCliente(Long idCliente) { this.idCliente = idCliente; }

    public String getDniCliente() { return dniCliente; }
    public void setDniCliente(String dniCliente) { this.dniCliente = dniCliente; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    // --- AQUÍ ESTÁ EL MÉTODO QUE DABA ERROR ---
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    // ------------------------------------------

    public Timestamp getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(Timestamp fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public int getActivo() { return activo; }
    public void setActivo(int activo) { this.activo = activo; }

    public Double getDeuda() { return deuda; }
    public void setDeuda(Double deuda) { this.deuda = deuda; }
}