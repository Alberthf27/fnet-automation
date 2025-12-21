package modelo;

import java.sql.Date;

public class Suscripcion {

    // ... (Tus campos privados igual que antes) ...
    private int idSuscripcion;
    private int idCliente;
    private int idServicio;
    private String nombreSuscripcion; // Nombre del contrato (por defecto = nombre cliente)
    private String codigoContrato;
    private Date fechaInicio;
    private String direccionInstalacion;
    private int activo;
    private String sector; // <--- NO OLVIDAR ESTE
    private int diaPago;
    private double garantia; // Nuevo atributo
    // Campos visuales
    private String nombreCliente;
    private String nombreServicio;
    private double montoMensual; // Primitivo double (inicia en 0.0, no null)
    private int facturasPendientes;
    private String historialPagos;

    private boolean mesAdelantado;
    private boolean equiposPrestados;

    // --- GETTERS Y SETTERS ---
    // Getter seguro para monto
    public double getMontoMensual() {
        return montoMensual;
    }

    public void setMontoMensual(double montoMensual) {
        this.montoMensual = montoMensual;
    }

    // Getters para Sector y Pendientes
    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public int getFacturasPendientes() {
        return facturasPendientes;
    }

    public void setFacturasPendientes(int fp) {
        this.facturasPendientes = fp;
    }

    public String getHistorialPagos() {
        return historialPagos;
    }

    public void setHistorialPagos(String h) {
        this.historialPagos = h;
    }

    // ... (Resto de getters/setters estándar para id, codigo, etc.) ...
    public int getIdSuscripcion() {
        return idSuscripcion;
    }

    public void setIdSuscripcion(int id) {
        this.idSuscripcion = id;
    }

    public String getCodigoContrato() {
        return codigoContrato;
    }

    public void setCodigoContrato(String c) {
        this.codigoContrato = c;
    }

    public String getDireccionInstalacion() {
        return direccionInstalacion;
    }

    public void setDireccionInstalacion(String d) {
        this.direccionInstalacion = d;
    }

    public Date getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Date f) {
        this.fechaInicio = f;
    }

    public int getActivo() {
        return activo;
    }

    public void setActivo(int a) {
        this.activo = a;
    }

    public int getDiaPago() {
        return diaPago;
    }

    public void setDiaPago(int d) {
        this.diaPago = d;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String n) {
        this.nombreCliente = n;
    }

    public String getNombreServicio() {
        return nombreServicio;
    }

    public void setNombreServicio(String n) {
        this.nombreServicio = n;
    }

    public double getGarantia() {
        return garantia;
    }

    public void setGarantia(double garantia) {
        this.garantia = garantia;
    }

    public String getNombreSuscripcion() {
        return nombreSuscripcion;
    }

    public void setNombreSuscripcion(String nombreSuscripcion) {
        this.nombreSuscripcion = nombreSuscripcion;
    }

    public boolean isMesAdelantado() {
        return mesAdelantado;
    }

    public void setMesAdelantado(boolean mesAdelantado) {
        this.mesAdelantado = mesAdelantado;
        // Si viene de la BD como int (0 o 1), asegúrate de convertirlo en el DAO
    }

    // Sobrecarga para facilitar la vida al DAO si usas int
    public void setMesAdelantado(int valor) {
        this.mesAdelantado = (valor == 1);
    }

    public boolean isEquiposPrestados() {
        return equiposPrestados;
    }

    public void setEquiposPrestados(boolean equiposPrestados) {
        this.equiposPrestados = equiposPrestados;
    }

    public void setEquiposPrestados(int valor) {
        this.equiposPrestados = (valor == 1);
    }
}
