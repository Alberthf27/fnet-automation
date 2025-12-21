package servicio;

/**
 * Interface para servicios de control del router.
 */
public interface IRouterService {
    boolean cortarServicio(String ipCliente);

    boolean reconectarServicio(String ipCliente);

    boolean verificarConexion();

    String getTipoRouter();
}
