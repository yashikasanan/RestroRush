package manager;

import model.Dish;
import model.OrderItem;
import java.util.ArrayList;
import java.util.List;

public class MenuManager {
    public static final int LOW_STOCK_THRESHOLD = 3;

    private final List<Dish> menu = new ArrayList<>();

    public MenuManager() {
        menu.add(new Dish(1, "Burger", 150, 18, 20));
        menu.add(new Dish(2, "Pizza", 250, 30, 15));
        menu.add(new Dish(3, "Pasta", 180, 24, 15));
        menu.add(new Dish(4, "Sandwich", 100, 16, 20));
        menu.add(new Dish(5, "Coffee", 80, 12, 30));
        menu.add(new Dish(6, "Noodles", 130, 22, 15));
        menu.add(new Dish(7, "French Fries", 90, 14, 25));
        menu.add(new Dish(8, "Garlic Bread", 110, 15, 20));
        menu.add(new Dish(9, "Spring Rolls", 140, 18, 18));
        menu.add(new Dish(10, "Fried Rice", 160, 22, 15));
        menu.add(new Dish(11, "Chocolate Brownie", 120, 15, 20));
        menu.add(new Dish(12, "Iced Tea", 70, 10, 30));
    }

    public synchronized void displayMenu() {
        System.out.println("\n-------------------- RESTAURANT MENU --------------------");
        System.out.printf("%-5s %-20s %-10s %-12s %-14s%n",
                "ID", "Dish", "Price", "Prep (sec)", "Quantity");
        System.out.println("-------------------------------------------------------------");
        for (Dish dish : menu) {
            int qty = dish.getAvailableQuantity();
            String qtyLabel = String.valueOf(qty);
            if (qty == 0) {
                qtyLabel = "0 (OUT!)";
            } else if (qty <= LOW_STOCK_THRESHOLD) {
                qtyLabel = qty + " (LOW)";
            }
            System.out.printf("%-5d %-20s Rs.%-7.2f %-12d %-14s%n",
                    dish.getDishId(), dish.getDishName(), dish.getPrice(),
                    dish.getPreparationTimeSeconds(), qtyLabel);
        }
        System.out.println("-------------------------------------------------------------");
    }

    public synchronized Dish getDishById(int id) {
        for (Dish dish : menu) {
            if (dish.getDishId() == id) return dish;
        }
        return null;
    }

    public synchronized boolean isAvailable(int id, int quantity) {
        Dish dish = getDishById(id);
        return dish != null && dish.hasQuantity(quantity);
    }

    // Reduce stock for all items, only if every item is available.
    public synchronized boolean reserveItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (!item.getDish().hasQuantity(item.getQuantity())) return false;
        }
        for (OrderItem item : items) {
            item.getDish().reduceQuantity(item.getQuantity());
        }
        checkLowStockAlerts(items);
        return true;
    }

    private void checkLowStockAlerts(List<OrderItem> items) {
        for (OrderItem item : items) {
            Dish dish = item.getDish();
            int remaining = dish.getAvailableQuantity();
            if (remaining == 0) {
                System.out.println("\n[OUT OF STOCK] " + dish.getDishName()
                        + " (ID " + dish.getDishId() + ") is now SOLD OUT! Refill via Option 10 or next order.");
            } else if (remaining <= LOW_STOCK_THRESHOLD) {
                System.out.println("\n[LOW STOCK ALERT] " + dish.getDishName()
                        + " (ID " + dish.getDishId() + ") has only " + remaining + " left!");
            }
        }
    }

    public synchronized void restockDish(int id, int quantity) {
        Dish dish = getDishById(id);
        if (dish == null) throw new IllegalArgumentException("Dish ID not found.");
        if (quantity <= 0) throw new IllegalArgumentException("Restock quantity must be positive.");
        dish.addQuantity(quantity);
    }

    public synchronized int restockAllLowOrEmpty(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Restock quantity must be positive.");
        int updated = 0;
        for (Dish dish : menu) {
            if (dish.getAvailableQuantity() <= LOW_STOCK_THRESHOLD) {
                dish.addQuantity(quantity);
                updated++;
            }
        }
        return updated;
    }
}
