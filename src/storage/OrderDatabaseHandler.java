package storage;

import model.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles database persistence and display for completed order history using MySQL.
 * Includes automatic fallback and synchronized file backup so the program never crashes.
 */
public class OrderDatabaseHandler {
    private final FileHandler fallbackFileHandler;

    public OrderDatabaseHandler() {
        this.fallbackFileHandler = new FileHandler("data/orders.txt");
        DatabaseConnection.initialize();
    }

    public synchronized boolean saveCompletedOrder(Order order) {
        return saveCompletedOrder(order, "Chef " + (order.getAssignedChefId() > 0 ? order.getAssignedChefId() : ""));
    }

    public synchronized boolean saveCompletedOrder(Order order, String chefName) {
        boolean savedToDb = false;

        if (DatabaseConnection.isAvailable()) {
            String insertSQL = "INSERT INTO order_history (order_id, customer_name, order_type, total_bill, status, items_summary, chef_name, completed_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(insertSQL)) {

                stmt.setInt(1, order.getOrderId());
                stmt.setString(2, order.getCustomerName());
                stmt.setString(3, order.getOrderTypeString());
                stmt.setDouble(4, order.calculateTotal());
                stmt.setString(5, order.getStatus().toString());
                stmt.setString(6, order.getDishSummary());
                stmt.setString(7, chefName != null ? chefName : "Chef");
                stmt.setTimestamp(8, new Timestamp(System.currentTimeMillis()));

                stmt.executeUpdate();
                savedToDb = true;
            } catch (SQLException e) {
                System.err.println("[DB ERROR] Failed to save order ORD" + order.getOrderId() + " to MySQL: " + e.getMessage());
            }
        }

        // Always save to fallback fileHandler as secondary persistent backup
        fallbackFileHandler.saveCompletedOrder(order);
        return savedToDb;
    }

    public void displayOrderHistory() {
        boolean displayedFromDb = false;

        if (DatabaseConnection.isAvailable()) {
            String querySQL = "SELECT order_id, customer_name, order_type, total_bill, status, chef_name, completed_at, items_summary "
                    + "FROM order_history ORDER BY id ASC";
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(querySQL)) {

                System.out.println("\n-------------------------------- SAVED ORDER HISTORY (MYSQL DATABASE) --------------------------------");
                System.out.printf("%-8s %-14s %-10s %-10s %-10s %-10s %-20s %s%n",
                        "Order", "Customer", "Type", "Bill", "Status", "Chef", "Completed At", "Items");
                System.out.println("------------------------------------------------------------------------------------------------------");

                boolean hasRows = false;
                while (rs.next()) {
                    hasRows = true;
                    int orderId = rs.getInt("order_id");
                    String customer = rs.getString("customer_name");
                    String orderType = rs.getString("order_type");
                    double bill = rs.getDouble("total_bill");
                    String status = rs.getString("status");
                    String chef = rs.getString("chef_name");
                    Timestamp ts = rs.getTimestamp("completed_at");
                    String items = rs.getString("items_summary");

                    String timeStr = "N/A";
                    if (ts != null) {
                        String raw = ts.toString();
                        timeStr = raw.substring(0, Math.min(19, raw.length()));
                    }
                    System.out.printf("ORD%-5d %-14s %-10s Rs.%-7.2f %-10s %-10s %-20s %s%n",
                            orderId, customer, (orderType != null ? orderType : "Dine-In"),
                            bill, status, (chef != null ? chef : "N/A"), timeStr, items);
                }

                if (!hasRows) {
                    System.out.println("No saved order history found in MySQL database.");
                }
                System.out.println("------------------------------------------------------------------------------------------------------");
                displayedFromDb = true;
            } catch (SQLException e) {
                System.err.println("[DB ERROR] Failed to query MySQL order history: " + e.getMessage());
            }
        }

        if (!displayedFromDb) {
            System.out.println("\n[NOTE] MySQL is currently unreachable. Displaying saved history from local file backup instead:");
            fallbackFileHandler.displayOrderHistory();
        }
    }

    public static class SavedReportStats {
        public final int totalOrders;
        public final int addOnOrders;
        public final double totalRevenue;
        public final String source;

        public SavedReportStats(int totalOrders, int addOnOrders, double totalRevenue, String source) {
            this.totalOrders = totalOrders;
            this.addOnOrders = addOnOrders;
            this.totalRevenue = totalRevenue;
            this.source = source;
        }
    }

    public SavedReportStats getSavedReportStats() {
        if (DatabaseConnection.isAvailable()) {
            String sql = "SELECT COUNT(*) AS cnt, "
                    + "COALESCE(SUM(CASE WHEN order_type = 'Add-On' THEN 1 ELSE 0 END), 0) AS addon_cnt, "
                    + "COALESCE(SUM(total_bill), 0) AS rev "
                    + "FROM order_history";
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new SavedReportStats(
                            rs.getInt("cnt"),
                            rs.getInt("addon_cnt"),
                            rs.getDouble("rev"),
                            "MySQL");
                }
            } catch (SQLException ignored) {}
        }

        return fallbackFileHandler.getSavedStats();
    }

    public Map<String, Integer> getChefSavedOrderCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (DatabaseConnection.isAvailable()) {
            String sql = "SELECT chef_name, COUNT(*) AS cnt FROM order_history "
                    + "WHERE chef_name IS NOT NULL GROUP BY chef_name";
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    counts.put(rs.getString("chef_name"), rs.getInt("cnt"));
                }
            } catch (SQLException ignored) {}
        }
        return counts;
    }
}
