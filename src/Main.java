import manager.ChefManager;
import manager.MenuManager;
import manager.OrderManager;
import manager.ReportManager;
import model.Dish;
import model.Order;
import model.OrderItem;
import service.WaitTimeEstimator;
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
        ReportManager reportManager = new ReportManager(orderManager, chefManager, dbHandler);
        WaitTimeEstimator waitEstimator = new WaitTimeEstimator(chefManager, orderManager);

        System.out.println("==========================================");
        System.out.println("              RESTRO RUSH");
        System.out.println("    RESTAURANT MANAGEMENT SIMULATOR");
        System.out.println("==========================================");

        boolean running = true;
        while (running) {
            printMenu();
            chefManager.setAwaitingMenuChoice(true);
            int choice = readInt("Enter your choice: ");
            chefManager.setAwaitingMenuChoice(false);
            if (choice == Integer.MIN_VALUE) {
                break;
            }

            switch (choice) {
                case 1:
                    menuManager.displayMenu();
                    break;
                case 2:
                    placeNewOrder(menuManager, orderManager, chefManager, waitEstimator);
                    break;
                case 3:
                    placeAddOnOrder(menuManager, orderManager, chefManager, waitEstimator);
                    break;
                case 4:
                    orderManager.displayAllOrders();
                    break;
                case 5:
                    cancelOrder(orderManager);
                    break;
                case 6:
                    chefManager.displayChefStatus();
                    break;
                case 7:
                    reportManager.generateRevenueReport();
                    break;
                case 8:
                    reportManager.displayChefPerformance();
                    break;
                case 9:
                    dbHandler.displayOrderHistory();
                    break;
                case 10:
                    restockDish(menuManager);
                    break;
                case 11:
                    waitEstimator.displayCurrentWaitTimes();
                    break;
                case 12:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Please select 1-12.");
            }
        }

        System.out.println("\nClosing restaurant. Waiting for all orders to finish...");
        chefManager.shutdownChefs();
        reportManager.generateRevenueReport();
        System.out.println("Thank you for using RestroRush!");
        scanner.close();
    }

    private static void printMenu() {
        synchronized (ChefManager.CONSOLE_LOCK) {
            System.out.println("\n--------------- MAIN MENU ---------------");
            System.out.println("1. View Menu");
            System.out.println("2. Place New Order");
            System.out.println("3. Place Add-On Order (Eating Customer Priority)");
            System.out.println("4. View All Orders");
            System.out.println("5. Cancel Pending Order");
            System.out.println("6. View Chef / Kitchen Status");
            System.out.println("7. Generate Revenue Report");
            System.out.println("8. View Chef Performance");
            System.out.println("9. View Saved Order History");
            System.out.println("10. Restock Dish Quantity");
            System.out.println("11. Check Estimated Wait Times");
            System.out.println("12. Exit");
            System.out.println("-----------------------------------------");
        }
    }

    private static void placeNewOrder(MenuManager menuManager,
                                      OrderManager orderManager,
                                      ChefManager chefManager,
                                      WaitTimeEstimator waitEstimator) {
        menuManager.displayMenu();
        System.out.print("Enter customer name: ");
        if (!scanner.hasNextLine()) return;
        String customerName = scanner.nextLine().trim();
        if (customerName.isEmpty()) {
            System.out.println("Customer name cannot be empty.");
            return;
        }

        int priority = -1;
        int parentOrderId = -1;
        Order existingDiningOrder = orderManager.findLatestCompletedDineInOrderByCustomer(customerName);
        if (existingDiningOrder != null) {
            System.out.println("\n[ACTIVE DINING DETECTED] " + existingDiningOrder.getCustomerName()
                    + " already has completed Dine-In order ORD" + existingDiningOrder.getOrderId()
                    + " (" + existingDiningOrder.getDishSummary() + ").");
            if (readYesNo("Are they currently eating and want Add-On Priority? (y/n): ")) {
                priority = Order.PRIORITY_ADD_ON;
                parentOrderId = existingDiningOrder.getOrderId();
            }
        }

        List<OrderItem> items = collectOrderItems(menuManager);
        if (items.isEmpty()) {
            System.out.println("Order was not placed because no items were selected.");
            return;
        }

        if (priority == -1) {
            while (true) {
                System.out.println("\nSelect order type:");
                System.out.println("1. Dine-In  (High Priority - Prepared First)");
                System.out.println("2. Takeaway (Normal Priority)");
                int choice = readInt("Choice: ");
                if (choice == Integer.MIN_VALUE) return;
                if (choice == 1) {
                    priority = Order.PRIORITY_DINE_IN;
                    break;
                } else if (choice == 2) {
                    priority = Order.PRIORITY_TAKEAWAY;
                    break;
                }
                System.out.println("Please enter 1 for Dine-In or 2 for Takeaway.");
            }
        }

        submitAndDispatchOrder(menuManager, orderManager, chefManager, waitEstimator,
                customerName, items, priority, parentOrderId);
    }

    private static void placeAddOnOrder(MenuManager menuManager,
                                        OrderManager orderManager,
                                        ChefManager chefManager,
                                        WaitTimeEstimator waitEstimator) {
        List<Order> eligibleOrders = orderManager.getCompletedDineInOrders();
        if (eligibleOrders.isEmpty()) {
            if (orderManager.hasInProgressDineInOrders()) {
                System.out.println("\nNo Dine-In orders have arrived yet (still being prepared).");
                System.out.println("Add-On priority unlocks once a Dine-In customer's food arrives (COMPLETED).");
            } else {
                System.out.println("\nNo seated Dine-In customers found with arrived food.");
                System.out.println("Place a Dine-In order first (Option 2).");
            }
            return;
        }

        System.out.println("\n------- CUSTOMERS CURRENTLY DINING (FOOD ARRIVED) -------");
        for (Order eligible : eligibleOrders) {
            System.out.printf("ORD%d | %-12s | %-10s | Rs. %.2f | %s%n",
                    eligible.getOrderId(), eligible.getCustomerName(),
                    eligible.getOrderTypeString(), eligible.calculateTotal(),
                    eligible.getDishSummary());
        }
        System.out.println("---------------------------------------------------------");

        int originalId = readInt("Enter existing Order number for Add-On (e.g. 101, or 0 to cancel): ");
        if (originalId == 0 || originalId == Integer.MIN_VALUE) return;

        String validationError = orderManager.validateAddOnEligibility(originalId);
        if (validationError != null) {
            System.out.println(validationError);
            return;
        }

        Order originalOrder = orderManager.findOrder(originalId);
        System.out.println("\n[PRIORITY ADD-ON] Adding items for seated customer "
                + originalOrder.getCustomerName() + " (ORD" + originalOrder.getOrderId() + ")");

        menuManager.displayMenu();
        List<OrderItem> items = collectOrderItems(menuManager);
        if (items.isEmpty()) {
            System.out.println("Add-On order was not placed because no items were selected.");
            return;
        }

        submitAndDispatchOrder(menuManager, orderManager, chefManager, waitEstimator,
                originalOrder.getCustomerName(), items, Order.PRIORITY_ADD_ON, originalOrder.getOrderId());
    }

    private static List<OrderItem> collectOrderItems(MenuManager menuManager) {
        List<OrderItem> items = new ArrayList<>();
        while (true) {
            int dishId = readInt("Enter dish ID (0 to finish): ");
            if (dishId == 0 || dishId == Integer.MIN_VALUE) break;

            Dish dish = menuManager.getDishById(dishId);
            if (dish == null) {
                System.out.println("Invalid dish ID.");
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
            int inCart = (existing == null ? 0 : existing.getQuantity());

            if (dish.getAvailableQuantity() - inCart <= 0) {
                System.out.println("[OUT OF STOCK] " + dish.getDishName()
                        + " has no remaining stock available"
                        + (inCart > 0 ? " (you already have " + inCart + " in this order)." : "."));
                if (readYesNo("Would you like to refill/restock " + dish.getDishName() + " now? (y/n): ")) {
                    int refillQty = readInt("Enter quantity to add: ");
                    if (refillQty == Integer.MIN_VALUE) break;
                    try {
                        menuManager.restockDish(dishId, refillQty);
                        System.out.println(dish.getDishName() + " refilled! Available now: "
                                + dish.getAvailableQuantity());
                    } catch (IllegalArgumentException e) {
                        System.out.println(e.getMessage());
                        continue;
                    }
                } else {
                    continue;
                }
            }

            int quantity = readInt("Enter quantity: ");
            if (quantity == Integer.MIN_VALUE) break;
            if (quantity <= 0) {
                System.out.println("Quantity must be positive.");
                continue;
            }

            int requestedTotal = quantity + inCart;
            if (!menuManager.isAvailable(dishId, requestedTotal)) {
                int remainingCanAdd = Math.max(0, dish.getAvailableQuantity() - inCart);
                System.out.println("Not enough quantity available. Stock: "
                        + dish.getAvailableQuantity()
                        + (inCart > 0 ? " (already in cart: " + inCart + ", can add up to " + remainingCanAdd + ")" : ""));
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
        return items;
    }

    private static void submitAndDispatchOrder(MenuManager menuManager,
                                               OrderManager orderManager,
                                               ChefManager chefManager,
                                               WaitTimeEstimator waitEstimator,
                                               String customerName,
                                               List<OrderItem> items,
                                               int priority,
                                               int parentOrderId) {
        // Reserve quantities only after the complete order has been validated.
        if (!menuManager.reserveItems(items)) {
            System.out.println("Order not placed. Stock changed, please try again.");
            return;
        }

        int orderId = orderManager.generateOrderId();
        Order order = new Order(orderId, customerName, items, priority, parentOrderId);
        orderManager.placeOrder(order);

        System.out.println("\nOrder ORD" + orderId + " (" + order.getDetailedTypeString() + ") placed successfully!");
        System.out.printf("Total bill: Rs. %.2f%n", order.calculateTotal());

        if (order.isAddOn()) {
            int queuePos = orderManager.getQueuePosition(orderId);
            int totalPending = orderManager.getPendingOrders().size();
            if (queuePos > 0) {
                int jumped = Math.max(0, totalPending - queuePos);
                if (jumped > 0) {
                    System.out.println("[PRIORITY FAST-TRACK] Queue position: #" + queuePos
                            + " (jumped ahead of " + jumped + " waiting order(s))!");
                } else {
                    System.out.println("[PRIORITY FAST-TRACK] Queue position: #" + queuePos
                            + " (highest priority for eating customer)!");
                }
            }
        }

        int waitSec = waitEstimator.estimateWaitSeconds(order);
        System.out.println("Estimated wait time: ~" + WaitTimeEstimator.formatTime(waitSec));
        chefManager.dispatchPendingOrders();

        // Brief delay (200ms) to let '[KITCHEN] started' print before menu redrawing
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
    }

    private static boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) return false;
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) return true;
            if (input.equals("n") || input.equals("no")) return false;
            System.out.println("Please enter 'y' or 'n'.");
        }
    }

    private static void cancelOrder(OrderManager orderManager) {
        int id = readInt("Enter order number to cancel (e.g. 101): ");
        if (id == Integer.MIN_VALUE) return;
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
        int id = readInt("Enter dish ID to restock (-1 to refill all low/empty dishes): ");
        if (id == Integer.MIN_VALUE) return;
        int quantity = readInt("Enter quantity to add: ");
        if (quantity == Integer.MIN_VALUE) return;
        try {
            if (id == -1) {
                int count = menuManager.restockAllLowOrEmpty(quantity);
                System.out.println(count > 0
                        ? "Refilled " + count + " low/out-of-stock dish(es) by +" + quantity + "."
                        : "No dishes are currently low or out of stock.");
            } else {
                menuManager.restockDish(id, quantity);
                System.out.println("Dish quantity updated successfully.");
            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            synchronized (ChefManager.CONSOLE_LOCK) {
                System.out.print(prompt);
            }
            if (!scanner.hasNextLine()) return Integer.MIN_VALUE;
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }
}
