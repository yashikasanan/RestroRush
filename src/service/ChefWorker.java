package service;

import manager.ChefManager;
import model.Chef;
import model.Order;
import storage.OrderDatabaseHandler;

public class ChefWorker implements Runnable {
    private final Chef chef;
    private final Order order;
    private final ChefManager chefManager;
    private final OrderDatabaseHandler dbHandler;

    public ChefWorker(Chef chef, Order order, ChefManager chefManager, OrderDatabaseHandler dbHandler) {
        this.chef = chef;
        this.order = order;
        this.chefManager = chefManager;
        this.dbHandler = dbHandler;
    }

    @Override
    public void run() {
        try {
            order.setStatus(Order.Status.PREPARING);
            synchronized (ChefManager.CONSOLE_LOCK) {
                System.out.println("\n[KITCHEN]  " + chef.getChefName()
                        + " started ORD" + order.getOrderId()
                        + " (" + order.getDetailedTypeString() + ")"
                        + " - " + order.getDishSummary());
                if (!chefManager.isClosed() && chefManager.isAwaitingMenuChoice()) {
                    System.out.print("Enter your choice: ");
                }
            }

            chef.setExpectedFreeTimeMs(System.currentTimeMillis() + order.getPreparationTimeSeconds() * 1000L);
            Thread.sleep(order.getPreparationTimeSeconds() * 1000L);

            order.setStatus(Order.Status.COMPLETED);
            chef.incrementCompletedOrders();
            boolean saved = dbHandler.saveCompletedOrder(order, chef.getChefName());

            synchronized (ChefManager.CONSOLE_LOCK) {
                System.out.println("\n--------------------------------------------------------------------------------");
                System.out.println("[READY]    " + chef.getChefName()
                        + " completed ORD" + order.getOrderId()
                        + " (" + order.getDetailedTypeString() + ")"
                        + " | Bill: Rs. " + String.format("%.2f", order.calculateTotal())
                        + (saved ? " | Saved to MySQL" : " | Saved to local backup"));
                System.out.println("--------------------------------------------------------------------------------");

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
