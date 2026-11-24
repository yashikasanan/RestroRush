package storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages JDBC connection lifecycle and database initialization for MySQL.
 */
public class DatabaseConnection {
    private static boolean driverLoaded = false;
    private static boolean initialized = false;
    private static boolean available = false;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[DB ERROR] MySQL JDBC Driver not found in classpath.");
            System.err.println("Make sure 'lib/mysql-connector-j-8.4.0.jar' is included in your classpath or project libraries.");
        }
    }

    public static synchronized void initialize() {
        if (initialized) return;
        DatabaseConfig.loadConfig();

        if (!driverLoaded) {
            initialized = true;
            available = false;
            return;
        }

        Connection conn = null;
        try {
            // Attempt to connect directly to the target database
            conn = DriverManager.getConnection(
                    DatabaseConfig.getUrl(),
                    DatabaseConfig.getUser(),
                    DatabaseConfig.getPassword()
            );
        } catch (SQLException e) {
            // Error 1049: ER_BAD_DB_ERROR (Unknown database)
            if (e.getErrorCode() == 1049) {
                System.out.println("[DB] Database '" + DatabaseConfig.getDatabaseName() + "' not found. Attempting auto-creation...");
                boolean created = createDatabaseIfNotExists();
                if (created) {
                    try {
                        conn = DriverManager.getConnection(
                                DatabaseConfig.getUrl(),
                                DatabaseConfig.getUser(),
                                DatabaseConfig.getPassword()
                        );
                    } catch (SQLException ex) {
                        handleConnectionError(ex);
                    }
                }
            } else {
                handleConnectionError(e);
            }
        }

        if (conn != null) {
            try {
                createOrderHistoryTable(conn);
                available = true;
                System.out.println("[DB] Connected to MySQL database '" + DatabaseConfig.getDatabaseName() + "' successfully.");
            } catch (SQLException e) {
                System.err.println("[DB ERROR] Failed to verify/create 'order_history' table: " + e.getMessage());
                available = false;
            } finally {
                try {
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }

        initialized = true;
    }

    private static boolean createDatabaseIfNotExists() {
        String baseServerUrl = DatabaseConfig.getBaseServerUrl();
        String dbName = DatabaseConfig.getDatabaseName();

        try (Connection serverConn = DriverManager.getConnection(
                baseServerUrl,
                DatabaseConfig.getUser(),
                DatabaseConfig.getPassword());
             Statement stmt = serverConn.createStatement()) {

            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
            System.out.println("[DB] Database '" + dbName + "' created successfully.");
            return true;
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Could not automatically create database '" + dbName + "': " + e.getMessage());
            System.err.println("[DB HINT] You can create it manually by running 'schema.sql' in MySQL.");
            return false;
        }
    }

    private static void createOrderHistoryTable(Connection conn) throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS order_history ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "order_id INT NOT NULL, "
                + "customer_name VARCHAR(100) NOT NULL, "
                + "order_type VARCHAR(20) NOT NULL DEFAULT 'Dine-In', "
                + "total_bill DECIMAL(10, 2) NOT NULL, "
                + "status VARCHAR(20) NOT NULL, "
                + "items_summary TEXT NOT NULL, "
                + "chef_name VARCHAR(50), "
                + "completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
            // Ensure column order_type exists if table was created in an earlier session
            try {
                stmt.executeUpdate("ALTER TABLE order_history ADD COLUMN order_type VARCHAR(20) DEFAULT 'Dine-In'");
            } catch (SQLException ignored) {
                // Column already exists
            }
        }
    }

    private static void handleConnectionError(SQLException e) {
        available = false;
        if (e.getErrorCode() == 1045) { // ER_ACCESS_DENIED_ERROR
            System.err.println("\n[DB ERROR] MySQL Access Denied for user '" + DatabaseConfig.getUser() + "'.");
            System.err.println("[ACTION REQUIRED] Please update your personal MySQL password in 'db.properties'.");
            System.err.println("(Each team member has their own gitignored 'db.properties' file).\n");
        } else {
            System.err.println("\n[DB WARNING] Could not connect to MySQL server: " + e.getMessage());
            System.err.println("[ACTION REQUIRED] Ensure your MySQL service is running.");
            System.err.println("(Orders will be saved to local file 'data/orders.txt' as a backup in the meantime).\n");
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            initialize();
        }
        return DriverManager.getConnection(
                DatabaseConfig.getUrl(),
                DatabaseConfig.getUser(),
                DatabaseConfig.getPassword()
        );
    }

    public static boolean isAvailable() {
        if (!initialized) {
            initialize();
        }
        return available;
    }
}
