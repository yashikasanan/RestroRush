package manager;

import model.Dish;
import model.OrderItem;
import java.util.ArrayList;
import java.util.List;

public class MenuManager {
    private final List<Dish> menu = new ArrayList<>();

    public MenuManager() {
        menu.add(new Dish(1, "Burger", 150, 5, 20));
        menu.add(new Dish(2, "Pizza", 250, 10, 15));
        menu.add(new Dish(3, "Pasta", 180, 7, 15));
        menu.add(new Dish(4, "Sandwich", 100, 4, 20));
        menu.add(new Dish(5, "Coffee", 80, 3, 30));
        menu.add(new Dish(6, "Noodles", 130, 6, 15));
        menu.add(new Dish(7, "French Fries", 90, 3, 25));
        menu.add(new Dish(8, "Garlic Bread", 110, 4, 20));
        menu.add(new Dish(9, "Spring Rolls", 140, 5, 18));
        menu.add(new Dish(10, "Fried Rice", 160, 6, 15));
        menu.add(new Dish(11, "Chocolate Brownie", 120, 4, 20));
        menu.add(new Dish(12, "Iced Tea", 70, 2, 30));
    }

    public synchronized void displayMenu() {
        System.out.println("\n-------------------- RESTAURANT MENU --------------------");
        System.out.printf("%-5s %-20s %-10s %-12s %-10s%n",
                "ID", "Dish", "Price", "Prep (sec)", "Quantity");
        System.out.println("---------------------------------------------------------");
        for (Dish dish : menu) {
            System.out.printf("%-5d %-20s Rs.%-7.2f %-12d %-10d%n",
                    dish.getDishId(), dish.getDishName(), dish.getPrice(),
                    dish.getPreparationTimeSeconds(), dish.getAvailableQuantity());
        }
        System.out.println("---------------------------------------------------------");
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
        return true;
    }

    public synchronized void restockDish(int id, int quantity) {
        Dish dish = getDishById(id);
        if (dish == null) throw new IllegalArgumentException("Dish ID not found.");
        if (quantity <= 0) throw new IllegalArgumentException("Restock quantity must be positive.");
        dish.addQuantity(quantity);
    }
}
