package servicio;

import DAO.ConfiguracionDAO;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

/**
 * Implementación de Router Service para MikroTik.
 * 
 * ESTADO: Pendiente de recibir IPs de los routers administradores.
 * Una vez que tengas las IPs, configura en configuracion_sistema:
 * - mikrotik_ip: IP del router principal
 * - mikrotik_usuario: Usuario API del router
 * - mikrotik_password: Contraseña del usuario API
 * 
 * REQUISITOS EN EL ROUTER MIKROTIK:
 * 1. Habilitar API REST: /ip/service set api enabled=yes
 * 2. Crear usuario con permisos API
 * 3. Configurar reglas de firewall para permitir acceso remoto
 * 
 * La lógica de corte/reconexión usa Address Lists:
 * - Cortar: Agrega IP cliente a lista "MOROSOS"
 * - Reconectar: Quita IP cliente de lista "MOROSOS"
 * El router debe tener una regla que bloquee tráfico de IPs en "MOROSOS"
 */
public class MikroTikRouterService implements IRouterService {

    private final ConfiguracionDAO configDAO;
    private String routerIp;
    private String usuario;
    private String password;

    public MikroTikRouterService() {
        this.configDAO = new ConfiguracionDAO();
        cargarConfiguracion();
    }

    private void cargarConfiguracion() {
        this.routerIp = configDAO.obtenerValor(ConfiguracionDAO.MIKROTIK_IP);
        this.usuario = configDAO.obtenerValor(ConfiguracionDAO.MIKROTIK_USUARIO);
        this.password = configDAO.obtenerValor(ConfiguracionDAO.MIKROTIK_PASSWORD);
    }

    @Override
    public boolean cortarServicio(String ipCliente) {
        if (!verificarConfiguracion()) {
            System.err.println("❌ MikroTik: No configurado correctamente");
            return false;
        }

        System.out.println("🔴 MikroTik: Cortando servicio para IP " + ipCliente);

        // Agregar IP a la lista de morosos
        return ejecutarComandoMikroTik("add", ipCliente, "MOROSOS");
    }

    @Override
    public boolean reconectarServicio(String ipCliente) {
        if (!verificarConfiguracion()) {
            System.err.println("❌ MikroTik: No configurado correctamente");
            return false;
        }

        System.out.println("🟢 MikroTik: Reconectando servicio para IP " + ipCliente);

        // Remover IP de la lista de morosos
        return ejecutarComandoMikroTik("remove", ipCliente, "MOROSOS");
    }

    @Override
    public boolean verificarConexion() {
        if (!verificarConfiguracion()) {
            return false;
        }

        try {
            String urlStr = String.format("http://%s/rest/system/identity", routerIp);
            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            // Autenticación básica
            String auth = Base64.getEncoder().encodeToString((usuario + ":" + password).getBytes());
            con.setRequestProperty("Authorization", "Basic " + auth);

            int responseCode = con.getResponseCode();
            return responseCode == 200;

        } catch (Exception e) {
            System.err.println("❌ MikroTik: Error verificando conexión - " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getTipoRouter() {
        return "MikroTik RouterOS";
    }

    /**
     * Ejecuta un comando en el router MikroTik via API REST.
     * 
     * @param accion      "add" para agregar a lista, "remove" para quitar
     * @param ipCliente   IP del cliente a modificar
     * @param nombreLista Nombre de la address-list ("MOROSOS")
     */
    private boolean ejecutarComandoMikroTik(String accion, String ipCliente, String nombreLista) {
        try {
            String urlStr;
            String method;
            String body = null;

            if ("add".equals(accion)) {
                // POST /rest/ip/firewall/address-list
                urlStr = String.format("http://%s/rest/ip/firewall/address-list", routerIp);
                method = "PUT";
                body = String.format("{\"address\":\"%s\",\"list\":\"%s\",\"comment\":\"Auto-corte FNET\"}",
                        ipCliente, nombreLista);
            } else {
                // Primero buscar el ID del registro
                String idRegistro = buscarIdEnAddressList(ipCliente, nombreLista);
                if (idRegistro == null) {
                    System.out.println("⚠️ MikroTik: IP " + ipCliente + " no encontrada en lista " + nombreLista);
                    return true; // No es error, simplemente no existía
                }

                // DELETE /rest/ip/firewall/address-list/{id}
                urlStr = String.format("http://%s/rest/ip/firewall/address-list/%s", routerIp, idRegistro);
                method = "DELETE";
            }

            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);

            // Autenticación básica
            String auth = Base64.getEncoder().encodeToString((usuario + ":" + password).getBytes());
            con.setRequestProperty("Authorization", "Basic " + auth);
            con.setRequestProperty("Content-Type", "application/json");

            if (body != null) {
                con.setDoOutput(true);
                OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
                writer.write(body);
                writer.flush();
                writer.close();
            }

            int responseCode = con.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("✅ MikroTik: Comando ejecutado exitosamente");
                return true;
            } else {
                // Leer error
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                String response = in.readLine();
                in.close();
                System.err.println("❌ MikroTik: Error " + responseCode + " - " + response);
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ MikroTik: Error ejecutando comando - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Busca el ID de un registro en la address-list.
     */
    private String buscarIdEnAddressList(String ipCliente, String nombreLista) {
        try {
            String urlStr = String.format(
                    "http://%s/rest/ip/firewall/address-list?address=%s&list=%s",
                    routerIp, ipCliente, nombreLista);

            URL url = new URL(urlStr);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            String auth = Base64.getEncoder().encodeToString((usuario + ":" + password).getBytes());
            con.setRequestProperty("Authorization", "Basic " + auth);

            if (con.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Parsear JSON básico para obtener .id
                String json = response.toString();
                if (json.contains("\"id\"")) {
                    // Extraer ID del JSON (formato: [{".id":"*1234",...}])
                    int idStart = json.indexOf("\".id\":\"") + 7;
                    int idEnd = json.indexOf("\"", idStart);
                    return json.substring(idStart, idEnd);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ MikroTik: Error buscando en address-list - " + e.getMessage());
        }
        return null;
    }

    private boolean verificarConfiguracion() {
        return routerIp != null && !routerIp.isEmpty()
                && usuario != null && !usuario.isEmpty()
                && password != null && !password.isEmpty();
    }
}
