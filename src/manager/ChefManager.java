package manager;

import model.Chef;
import model.Order;
import service.ChefWorker;
import storage.OrderDatabaseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChefManager {
    private final List<Chef> chefs = new ArrayList<>();
    private final ExecutorService kitchen = Executors.newFixedThreadPool(4);
    private final OrderManager orderManager;
    private final OrderDatabaseHandler dbHandler;
    private volatile boolean closed = false;

    public ChefManager(OrderManager orderManager, OrderDatabaseHandler dbHandler) {
        this.orderManager = orderManager;
        this.dbHandler = dbHandler;
        chefs.add(new Chef(1, "Chef 1"));
        chefs.add(new Chef(2, "Chef 2"));
        chefs.add(new Chef(3, "Chef 3"));
        chefs.add(new Chef(4, "Chef 4"));
    }

    public boolean isClosed() {
        return closed;
    }

    // Give waiting orders to free chefs.
    public synchronized void dispatchPendingOrders() {
        if (closed) return;

        for (Chef chef : chefs) {
            if (!chef.isAvailable()) continue;

            Order order = orderManager.getNextOrder();
            if (order == null) break;

            chef.setAvailable(false);
            order.setAssignedChefId(chef.getChefId());
            kitchen.submit(new ChefWorker(chef, order, this, dbHandler));
        }
    }

    public synchronized void chefFinished(Chef chef) {
        chef.setAvailable(true);
        if (!closed) {
            dispatchPendingOrders();
        }
    }

    public synchronized void displayChefStatus() {
        System.out.println("\n--------------- KITCHEN STATUS ---------------");
        for (Chef chef : chefs) {
            String status = chef.isAvailable() ? "AVAILABLE" : "PREPARING";
            System.out.printf("%-10s | %-12s | Completed orders: %d%n",
                    chef.getChefName(), status, chef.getCompletedOrders());
        }
        System.out.println("-----------------------------------------------");
    }

    public synchronized List<Chef> getChefs() {
        return new ArrayList<>(chefs);
    }

    public void shutdownChefs() {
        // Wait until every queued order has been assigned to a chef
        while (!orderManager.getPendingOrders().isEmpty()) {
            dispatchPendingOrders();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        closed = true;
        kitchen.shutdown();
        try {
            if (!kitchen.awaitTermination(60, TimeUnit.SECONDS)) {
                kitchen.shutdownNow();
            }
        } catch (InterruptedException e) {
            kitchen.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
