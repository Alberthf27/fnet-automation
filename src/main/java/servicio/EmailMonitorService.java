package servicio;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Properties;

/**
 * Servicio para monitorear correos electrónicos y descargar archivos Excel de
 * Yape.
 * Se conecta vía IMAP a Gmail y busca correos con reportes de transacciones.
 */
public class EmailMonitorService {

    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String yapeSenderEmail;
    private final String downloadPath;

    public EmailMonitorService() {
        // Leer configuración de variables de entorno
        this.host = System.getenv().getOrDefault("EMAIL_HOST", "imap.gmail.com");
        this.port = System.getenv().getOrDefault("EMAIL_PORT", "993");
        this.username = System.getenv().getOrDefault("EMAIL_USER", "alberthflores47@gmail.com");
        this.password = System.getenv().getOrDefault("EMAIL_PASSWORD", "alberth789");
        this.yapeSenderEmail = System.getenv().getOrDefault("YAPE_SENDER_EMAIL", "notificaciones@yape.com.pe");

        // Carpeta temporal para descargar Excel
        this.downloadPath = System.getProperty("java.io.tmpdir") + File.separator + "yape_reports";
        crearDirectorioSiNoExiste();
    }

    private void crearDirectorioSiNoExiste() {
        try {
            Path path = Paths.get(downloadPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("📁 Directorio creado: " + downloadPath);
            }
        } catch (IOException e) {
            System.err.println("❌ Error creando directorio: " + e.getMessage());
        }
    }

    /**
     * Descarga nuevos reportes de Yape desde el correo.
     * 
     * @return Lista de archivos Excel descargados
     */
    public List<File> descargarNuevosReportes() {
        List<File> archivosDescargados = new ArrayList<>();

        try {
            System.out.println("\n📧 Conectando a correo: " + username);

            // Configurar propiedades para IMAP
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", port);
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.ssl.trust", "*");

            // Conectar al correo
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(host, username, password);

            // Abrir bandeja de entrada
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            System.out.println("✅ Conectado a correo. Buscando reportes de Yape...");

            // Buscar correos no leídos con asunto "Historial de Movimientos"
            SearchTerm searchTerm = new AndTerm(
                    new SubjectTerm("Historial de Movimientos"),
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            Message[] mensajes = inbox.search(searchTerm);
            System.out.println("📨 Encontrados " + mensajes.length + " correos nuevos de Yape");

            // Procesar cada mensaje
            for (Message mensaje : mensajes) {
                try {
                    File archivoDescargado = procesarMensaje(mensaje);
                    if (archivoDescargado != null) {
                        archivosDescargados.add(archivoDescargado);
                        // Marcar como leído
                        mensaje.setFlag(Flags.Flag.SEEN, true);
                        System.out.println("✅ Excel descargado y correo marcado como leído");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error procesando mensaje: " + e.getMessage());
                }
            }

            // Cerrar conexión
            inbox.close(false);
            store.close();

            System.out.println("📊 Total de archivos descargados: " + archivosDescargados.size());

        } catch (Exception e) {
            System.err.println("❌ Error conectando a correo: " + e.getMessage());
            e.printStackTrace();
        }

        return archivosDescargados;
    }

    /**
     * Procesa un mensaje de correo y descarga el archivo Excel adjunto.
     */
    private File procesarMensaje(Message mensaje) throws Exception {
        String subject = mensaje.getSubject();
        System.out.println("📬 Procesando: " + subject);

        // Verificar si tiene adjuntos
        if (mensaje.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) mensaje.getContent();

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);

                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    String fileName = bodyPart.getFileName();

                    // Verificar si es un archivo Excel de Yape
                    if (fileName != null && fileName.toLowerCase().contains("reportetransacciones")
                            && fileName.toLowerCase().endsWith(".xlsx")) {

                        // Descargar archivo
                        String filePath = downloadPath + File.separator + fileName;
                        File file = new File(filePath);

                        try (InputStream is = bodyPart.getInputStream();
                                FileOutputStream fos = new FileOutputStream(file)) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }

                        System.out.println("💾 Archivo descargado: " + fileName);
                        return file;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Elimina archivos Excel procesados para liberar espacio.
     */
    public void limpiarArchivosAntiguos() {
        try {
            File dir = new File(downloadPath);
            File[] archivos = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xlsx"));

            if (archivos != null) {
                for (File archivo : archivos) {
                    if (archivo.delete()) {
                        System.out.println("🗑️ Archivo eliminado: " + archivo.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error limpiando archivos: " + e.getMessage());
        }
    }

    /**
     * Verifica la conexión al correo.
     */
    public boolean verificarConexion() {
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", port);
            props.put("mail.imaps.ssl.enable", "true");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(host, username, password);
            store.close();

            System.out.println("✅ Conexión a correo verificada");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error verificando conexión: " + e.getMessage());
            return false;
        }
    }
}
