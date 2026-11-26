package storage;

import model.Order;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileHandler {
    private final String fileName;

    public FileHandler(String fileName) {
        this.fileName = fileName;
    }

    public synchronized void saveCompletedOrder(Order order) {
        File file = new File(fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write("ORD" + order.getOrderId() + ","
                    + order.getCustomerName().replace(",", " ") + ","
                    + String.format("%.2f", order.calculateTotal()) + ","
                    + order.getStatus() + ","
                    + order.getDishSummary().replace(",", ";"));
            writer.newLine();
        } catch (IOException e) {
            System.out.println("Could not save order history: " + e.getMessage());
        }
    }

    public void displayOrderHistory() {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("No saved order history yet.");
            return;
        }

        System.out.println("\n--------------- SAVED ORDER HISTORY ---------------");
        try (java.util.Scanner reader = new java.util.Scanner(file)) {
            System.out.printf("%-8s %-15s %-10s %-10s %s%n", "Order", "Customer", "Bill", "Status", "Items");
            while (reader.hasNextLine()) {
                String[] parts = reader.nextLine().split(",", 5);
                if (parts.length < 5) continue;
                System.out.printf("%-8s %-15s Rs.%-7s %-10s %s%n",
                        parts[0], parts[1], parts[2], parts[3], parts[4]);
            }
        } catch (IOException e) {
            System.out.println("Could not read order history: " + e.getMessage());
        }
        System.out.println("---------------------------------------------------");
    }

    public OrderDatabaseHandler.SavedReportStats getSavedStats() {
        File file = new File(fileName);
        if (!file.exists()) {
            return new OrderDatabaseHandler.SavedReportStats(0, 0, 0.0, "Local File");
        }
        int count = 0;
        double revenue = 0.0;
        try (java.util.Scanner reader = new java.util.Scanner(file)) {
            while (reader.hasNextLine()) {
                String[] parts = reader.nextLine().split(",", 5);
                if (parts.length < 5) continue;
                try {
                    revenue += Double.parseDouble(parts[2].trim());
                    count++;
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
        return new OrderDatabaseHandler.SavedReportStats(count, 0, revenue, "Local File");
    }
}
