package manager;

import model.Chef;
import model.Order;
import model.OrderItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportManager {
    private final OrderManager orderManager;
    private final ChefManager chefManager;

    public ReportManager(OrderManager orderManager, ChefManager chefManager) {
        this.orderManager = orderManager;
        this.chefManager = chefManager;
    }

    public void generateRevenueReport() {
        List<Order> orders = orderManager.getAllOrders();
        double revenue = 0;
        int completed = 0;
        int cancelled = 0;
        int pendingOrPreparing = 0;
        Map<String, Integer> dishCounts = new HashMap<>();

        for (Order order : orders) {
            if (order.getStatus() == Order.Status.COMPLETED) {
                completed++;
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

        String popularDish = "N/A";
        int highestCount = 0;
        for (Map.Entry<String, Integer> entry : dishCounts.entrySet()) {
            if (entry.getValue() > highestCount) {
                highestCount = entry.getValue();
                popularDish = entry.getKey();
            }
        }

        System.out.println("\n============== RESTAURANT REPORT ==============");
        System.out.println("Total orders       : " + orders.size());
        System.out.println("Completed orders   : " + completed);
        System.out.println("Cancelled orders   : " + cancelled);
        System.out.println("Pending/preparing  : " + pendingOrPreparing);
        System.out.printf("Total revenue      : Rs. %.2f%n", revenue);
        System.out.println("Most popular dish  : " + popularDish
                + (highestCount > 0 ? " (" + highestCount + " sold)" : ""));
        System.out.println("===============================================");
    }

    public void displayChefPerformance() {
        System.out.println("\n------------- CHEF PERFORMANCE -------------");
        for (Chef chef : chefManager.getChefs()) {
            System.out.println(chef.getChefName() + " completed "
                    + chef.getCompletedOrders() + " order(s).");
        }
        System.out.println("---------------------------------------------");
    }
}
