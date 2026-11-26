package service;

import manager.ChefManager;
import model.Chef;
import model.Order;
import storage.OrderDatabaseHandler;

public class ChefWorker implements Runnable {
    private final Chef chef;
    private final Order order;
    private final String assignedDishSummary;
    private final int prepSeconds;
    private final boolean isTeamOrder;
    private final ChefManager chefManager;
    private final OrderDatabaseHandler dbHandler;

    public ChefWorker(Chef chef,
                      Order order,
                      String assignedDishSummary,
                      int prepSeconds,
                      boolean isTeamOrder,
                      ChefManager chefManager,
                      OrderDatabaseHandler dbHandler) {
        this.chef = chef;
        this.order = order;
        this.assignedDishSummary = assignedDishSummary;
        this.prepSeconds = prepSeconds;
        this.isTeamOrder = isTeamOrder;
        this.chefManager = chefManager;
        this.dbHandler = dbHandler;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(prepSeconds * 1000L);

            chef.incrementCompletedOrders();
            boolean orderFinished = order.markChefCompleted(chef.getChefName());

            if (isTeamOrder) {
                synchronized (ChefManager.CONSOLE_LOCK) {
                    System.out.println("\n[DONE]     " + chef.getChefName()
                            + " prepared " + assignedDishSummary + " for ORD" + order.getOrderId());
                    if (!orderFinished && !chefManager.isClosed() && chefManager.isAwaitingMenuChoice()) {
                        System.out.print("Enter your choice: ");
                    }
                }
            }

            if (!orderFinished) {
                return;
            }

            order.setStatus(Order.Status.COMPLETED);
            String chefsSummary = order.getAssignedChefsSummary();
            boolean saved = dbHandler.saveCompletedOrder(order, chefsSummary);

            String line1 = "[ORDER READY] ORD" + order.getOrderId()
                    + " (" + order.getDetailedTypeString() + ") - " + order.getDishSummary();
            String line2 = "Prepared by: " + chefsSummary
                    + " | Bill: Rs. " + String.format("%.2f", order.calculateTotal())
                    + (saved ? " | Saved to MySQL" : " | Saved to local backup");
            int width = Math.max(line1.length(), line2.length()) + 2;
            String border = "+" + "-".repeat(width) + "+";

            synchronized (ChefManager.CONSOLE_LOCK) {
                System.out.println();
                System.out.println(border);
                System.out.printf("| %-" + (width - 2) + "s |%n", line1);
                System.out.printf("| %-" + (width - 2) + "s |%n", line2);
                System.out.println(border);

                // Only re-display main menu prompt if the user is actually waiting at the main menu
                if (!chefManager.isClosed() && chefManager.isAwaitingMenuChoice()) {
                    System.out.print("Enter your choice: ");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (ChefManager.CONSOLE_LOCK) {
                System.out.println("\n" + chef.getChefName() + " was interrupted while preparing ORD"
                        + order.getOrderId());
            }
        } finally {
            chefManager.chefFinished(chef);
        }
    }
}
