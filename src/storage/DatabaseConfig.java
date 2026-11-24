package storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages database configuration loaded from 'db.properties' or environment variables.
 * Designed for multi-user repositories where credentials must not be committed to GitHub.
 */
public class DatabaseConfig {
    private static final String CONFIG_FILE = "db.properties";
    private static final String EXAMPLE_FILE = "db.properties.example";

    private static String url;
    private static String user;
    private static String password;
    private static boolean loaded = false;

    public static synchronized void loadConfig() {
        if (loaded) return;

        Properties props = new Properties();
        File file = new File(CONFIG_FILE);

        if (!file.exists()) {
            createDefaultConfigFile(file);
        }

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("[CONFIG WARNING] Could not read " + CONFIG_FILE + ": " + e.getMessage());
            }
        }

        // Check environment variables first (allows CI/CD or docker override)
        String envUrl = System.getenv("DB_URL");
        String envUser = System.getenv("DB_USER");
        String envPassword = System.getenv("DB_PASSWORD");

        url = (envUrl != null && !envUrl.trim().isEmpty()) ? envUrl.trim() :
                props.getProperty("db.url", "jdbc:mysql://localhost:3306/restrorush_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC").trim();

        user = (envUser != null && !envUser.trim().isEmpty()) ? envUser.trim() :
                props.getProperty("db.user", "root").trim();

        password = (envPassword != null) ? envPassword :
                props.getProperty("db.password", "");

        loaded = true;
    }

    private static void createDefaultConfigFile(File target) {
        File example = new File(EXAMPLE_FILE);
        if (example.exists()) {
            try (FileInputStream in = new FileInputStream(example);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                System.out.println("[CONFIG] Generated local '" + CONFIG_FILE + "' from template.");
                System.out.println("[CONFIG] Remember to set your MySQL password in '" + CONFIG_FILE + "'.");
                return;
            } catch (IOException ignored) {}
        }

        try (FileOutputStream out = new FileOutputStream(target)) {
            String defaultContent = "# Local MySQL Configuration (Ignored by Git)\n"
                    + "db.url=jdbc:mysql://localhost:3306/restrorush_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC\n"
                    + "db.user=root\n"
                    + "db.password=\n";
            out.write(defaultContent.getBytes());
            System.out.println("[CONFIG] Created default '" + CONFIG_FILE + "'.");
        } catch (IOException e) {
            System.err.println("[CONFIG WARNING] Could not create " + CONFIG_FILE + ": " + e.getMessage());
        }
    }

    public static String getUrl() {
        if (!loaded) loadConfig();
        return url;
    }

    public static String getUser() {
        if (!loaded) loadConfig();
        return user;
    }

    public static String getPassword() {
        if (!loaded) loadConfig();
        return password;
    }

    /**
     * Extracts database name from JDBC URL (e.g., 'restrorush_db').
     */
    public static String getDatabaseName() {
        String currentUrl = getUrl();
        int slashIndex = currentUrl.lastIndexOf('/');
        if (slashIndex == -1) return "restrorush_db";
        int questionIndex = currentUrl.indexOf('?', slashIndex);
        if (questionIndex == -1) {
            return currentUrl.substring(slashIndex + 1);
        } else {
            return currentUrl.substring(slashIndex + 1, questionIndex);
        }
    }

    /**
     * Generates a connection URL to the MySQL root host without the database name.
     * Useful for automatically creating the database if it doesn't exist yet.
     */
    public static String getBaseServerUrl() {
        String currentUrl = getUrl();
        int slashIndex = currentUrl.lastIndexOf('/');
        if (slashIndex == -1) return currentUrl;
        int questionIndex = currentUrl.indexOf('?', slashIndex);
        String params = (questionIndex != -1) ? currentUrl.substring(questionIndex) : "";
        return currentUrl.substring(0, slashIndex + 1) + params;
    }
}

