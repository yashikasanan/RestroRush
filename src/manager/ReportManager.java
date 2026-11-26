package manager;

import model.Chef;
import model.Order;
import model.OrderItem;
import storage.OrderDatabaseHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportManager {
    private final OrderManager orderManager;
    private final ChefManager chefManager;
    private final OrderDatabaseHandler dbHandler;

    public ReportManager(OrderManager orderManager, ChefManager chefManager) {
        this(orderManager, chefManager, null);
    }

    public ReportManager(OrderManager orderManager, ChefManager chefManager, OrderDatabaseHandler dbHandler) {
        this.orderManager = orderManager;
        this.chefManager = chefManager;
        this.dbHandler = dbHandler;
    }

    public void generateRevenueReport() {
        List<Order> orders = orderManager.getAllOrders();
        double revenue = 0;
        int completed = 0;
        int addOnCompleted = 0;
        int cancelled = 0;
        int pendingOrPreparing = 0;
        Map<String, Integer> dishCounts = new HashMap<>();

        for (Order order : orders) {
            if (order.getStatus() == Order.Status.COMPLETED) {
                completed++;
                if (order.isAddOn())
                    addOnCompleted++;
                revenue += order.calculateTotal();
                for (OrderItem item : order.getItems()) {
                    dishCounts.put(item.getDish().getDishName(),
                            dishCounts.getOrDefault(item.getDish().getDishName(), 0)
                                    + item.getQuantity());
                }
            } else if (order.getStatus() == Order.Status.CANCELLED) {
                cancelled++;
            } else {
                pendingOrPreparing++;
            }
        }

        int highestCount = 0;
        for (int count : dishCounts.values()) {
            if (count > highestCount) {
                highestCount = count;
            }
        }
        List<String> topDishes = new ArrayList<>();
        if (highestCount > 0) {
            for (Map.Entry<String, Integer> entry : dishCounts.entrySet()) {
                if (entry.getValue() == highestCount) {
                    topDishes.add(entry.getKey());
                }
            }
        }
        String popularDishText = topDishes.isEmpty()
                ? "N/A"
                : String.join(", ", topDishes)
                        + " (" + highestCount + " sold" + (topDishes.size() > 1 ? " each" : "") + ")";

        System.out.println("\n============== RESTAURANT REPORT ==============");
        System.out.println("--- Current Session ---");
        System.out.println("Total orders       : " + orders.size());
        System.out.println("Completed orders   : " + completed);
        System.out.println("Add-On priority    : " + addOnCompleted);
        System.out.println("Cancelled orders   : " + cancelled);
        System.out.println("Pending/preparing  : " + pendingOrPreparing);
        System.out.printf("Session revenue    : Rs. %.2f%n", revenue);
        System.out.println("Most popular dish  : " + popularDishText);

        if (dbHandler != null) {
            OrderDatabaseHandler.SavedReportStats saved = dbHandler.getSavedReportStats();
            System.out.println("--- All-Time Saved (" + saved.source + ") ---");
            System.out.println("Saved completed    : " + saved.totalOrders);
            if ("MySQL".equals(saved.source)) {
                System.out.println("Saved Add-On       : " + saved.addOnOrders);
            }
            System.out.printf("All-time revenue   : Rs. %.2f%n", saved.totalRevenue);
        }
        System.out.println("===============================================");
    }

    public void displayChefPerformance() {
        Map<String, Integer> savedCounts = (dbHandler != null)
                ? dbHandler.getChefSavedOrderCounts()
                : new HashMap<>();

        System.out.println("\n------------- CHEF PERFORMANCE -------------");
        for (Chef chef : chefManager.getChefs()) {
            int sessionCount = chef.getCompletedOrders();
            Integer allTimeCount = savedCounts.get(chef.getChefName());
            if (allTimeCount != null) {
                System.out.println(chef.getChefName() + " completed "
                        + sessionCount + " order(s) this session | "
                        + allTimeCount + " total in MySQL.");
            } else {
                System.out.println(chef.getChefName() + " completed "
                        + sessionCount + " order(s).");
            }
        }
        System.out.println("---------------------------------------------");
    }
}
