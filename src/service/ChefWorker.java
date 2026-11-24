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
            System.out.println("\n[KITCHEN]  " + chef.getChefName()
                    + " started ORD" + order.getOrderId()
                    + " (" + order.getOrderTypeString() + ")"
                    + " - " + order.getDishSummary());

            Thread.sleep(order.getPreparationTimeSeconds() * 1000L);

            order.setStatus(Order.Status.COMPLETED);
            chef.incrementCompletedOrders();
            boolean saved = dbHandler.saveCompletedOrder(order, chef.getChefName());

            System.out.println("\n--------------------------------------------------------------------------------");
            System.out.println("[READY]    " + chef.getChefName()
                    + " completed ORD" + order.getOrderId()
                    + " (" + order.getOrderTypeString() + ")"
                    + " | Bill: Rs. " + String.format("%.2f", order.calculateTotal())
                    + (saved ? " | Saved to MySQL" : " | Saved to local backup"));
            System.out.println("--------------------------------------------------------------------------------");

            // If the user is waiting at the console prompt, re-display prompt cleanly
            if (!chefManager.isClosed()) {
                System.out.print("Enter your choice: ");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("\n" + chef.getChefName() + " was interrupted while preparing ORD"
                    + order.getOrderId());
        } finally {
            chefManager.chefFinished(chef);
        }
    }
}
