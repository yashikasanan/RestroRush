import manager.ChefManager;
import manager.MenuManager;
import manager.OrderManager;
import manager.ReportManager;
import model.Dish;
import model.Order;
import model.OrderItem;
import storage.OrderDatabaseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        MenuManager menuManager = new MenuManager();
        OrderManager orderManager = new OrderManager();
        OrderDatabaseHandler dbHandler = new OrderDatabaseHandler();
        ChefManager chefManager = new ChefManager(orderManager, dbHandler);
        ReportManager reportManager = new ReportManager(orderManager, chefManager);

        System.out.println("==========================================");
        System.out.println("              RESTRO RUSH");
        System.out.println("    RESTAURANT MANAGEMENT SIMULATOR");
        System.out.println("==========================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1:
                    menuManager.displayMenu();
                    break;
                case 2:
                    placeNewOrder(menuManager, orderManager, chefManager);
                    break;
                case 3:
                    orderManager.displayAllOrders();
                    break;
                case 4:
                    cancelOrder(orderManager);
                    break;
                case 5:
                    chefManager.displayChefStatus();
                    break;
                case 6:
                    reportManager.generateRevenueReport();
                    break;
                case 7:
                    reportManager.displayChefPerformance();
                    break;
                case 8:
                    dbHandler.displayOrderHistory();
                    break;
                case 9:
                    restockDish(menuManager);
                    break;
                case 10:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Please select 1-10.");
            }
        }

        System.out.println("\nClosing restaurant. Waiting for all orders to finish...");
        chefManager.shutdownChefs();
        reportManager.generateRevenueReport();
        System.out.println("Thank you for using RestroRush!");
        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n--------------- MAIN MENU ---------------");
        System.out.println("1. View Menu");
        System.out.println("2. Place New Order");
        System.out.println("3. View All Orders");
        System.out.println("4. Cancel Pending Order");
        System.out.println("5. View Chef / Kitchen Status");
        System.out.println("6. Generate Revenue Report");
        System.out.println("7. View Chef Performance");
        System.out.println("8. View Saved Order History");
        System.out.println("9. Restock Dish Quantity");
        System.out.println("10. Exit");
        System.out.println("-----------------------------------------");
    }

    private static void placeNewOrder(MenuManager menuManager,
                                      OrderManager orderManager,
                                      ChefManager chefManager) {
        menuManager.displayMenu();
        System.out.print("Enter customer name: ");
        String customerName = scanner.nextLine().trim();
        if (customerName.isEmpty()) {
            System.out.println("Customer name cannot be empty.");
            return;
        }

        List<OrderItem> items = new ArrayList<>();
        while (true) {
            int dishId = readInt("Enter dish ID (0 to finish): ");
            if (dishId == 0) break;

            Dish dish = menuManager.getDishById(dishId);
            if (dish == null) {
                System.out.println("Invalid dish ID.");
                continue;
            }

            int quantity = readInt("Enter quantity: ");
            if (quantity <= 0) {
                System.out.println("Quantity must be positive.");
                continue;
            }

            // Merge repeated selections of the same dish.
            OrderItem existing = null;
            for (OrderItem item : items) {
                if (item.getDish().getDishId() == dishId) {
                    existing = item;
                    break;
                }
            }

            int requestedTotal = quantity + (existing == null ? 0 : existing.getQuantity());
            if (!menuManager.isAvailable(dishId, requestedTotal)) {
                System.out.println("Not enough quantity available. Available: "
                        + dish.getAvailableQuantity());
                continue;
            }

            if (existing == null) {
                items.add(new OrderItem(dish, quantity));
            } else {
                items.remove(existing);
                items.add(new OrderItem(dish, requestedTotal));
            }
            System.out.println("Added: " + quantity + " x " + dish.getDishName());
        }

        if (items.isEmpty()) {
            System.out.println("Order was not placed because no items were selected.");
            return;
        }

        int priority;
        while (true) {
            System.out.println("\nSelect order type:");
            System.out.println("1. Dine-In  (High Priority - Prepared First)");
            System.out.println("2. Takeaway (Normal Priority)");
            priority = readInt("Choice: ");
            if (priority == 1 || priority == 2) break;
            System.out.println("Please enter 1 for Dine-In or 2 for Takeaway.");
        }

        // Reserve quantities only after the complete order has been validated.
        if (!menuManager.reserveItems(items)) {
            System.out.println("Order not placed. Stock changed, please try again.");
            return;
        }

        int orderId = orderManager.generateOrderId();
        Order order = new Order(orderId, customerName, items, priority);
        orderManager.placeOrder(order);

        System.out.println("\nOrder ORD" + orderId + " (" + order.getOrderTypeString() + ") placed successfully!");
        System.out.printf("Total bill: Rs. %.2f%n", order.calculateTotal());
        chefManager.dispatchPendingOrders();

        // Brief delay (200ms) to let '[KITCHEN] started' print before menu redrawing
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
    }

    private static void cancelOrder(OrderManager orderManager) {
        int id = readInt("Enter order number to cancel (e.g. 101): ");
        Order order = orderManager.findOrder(id);
        if (order == null) {
            System.out.println("Order not found.");
        } else if (!orderManager.cancelOrder(id)) {
            System.out.println("Cannot cancel. This order is already being prepared or is finished.");
        } else {
            // Return the dishes to stock.
            for (OrderItem item : order.getItems()) {
                item.getDish().addQuantity(item.getQuantity());
            }
            System.out.println("Order ORD" + id + " cancelled. Dishes returned to stock.");
        }
    }

    private static void restockDish(MenuManager menuManager) {
        menuManager.displayMenu();
        int id = readInt("Enter dish ID to restock: ");
        int quantity = readInt("Enter quantity to add: ");
        try {
            menuManager.restockDish(id, quantity);
            System.out.println("Dish quantity updated successfully.");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }
}
