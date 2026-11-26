package manager;

import model.Chef;
import model.Order;
import model.OrderItem;
import service.ChefWorker;
import storage.OrderDatabaseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChefManager {
    public static final Object CONSOLE_LOCK = new Object();

    private final List<Chef> chefs = new ArrayList<>();
    private final ExecutorService kitchen = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService agingMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PriorityAgingMonitor");
        t.setDaemon(true);
        return t;
    });
    private final OrderManager orderManager;
    private final OrderDatabaseHandler dbHandler;
    private volatile boolean closed = false;
    private volatile boolean awaitingMenuChoice = false;
    private int nextChefIndex = 0;

    public ChefManager(OrderManager orderManager, OrderDatabaseHandler dbHandler) {
        this.orderManager = orderManager;
        this.dbHandler = dbHandler;
        chefs.add(new Chef(1, "Chef 1"));
        chefs.add(new Chef(2, "Chef 2"));
        chefs.add(new Chef(3, "Chef 3"));
        chefs.add(new Chef(4, "Chef 4"));

        agingMonitor.scheduleAtFixedRate(() -> {
            if (!closed && orderManager.applyPriorityAging() > 0 && awaitingMenuChoice) {
                synchronized (CONSOLE_LOCK) {
                    System.out.print("Enter your choice: ");
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isAwaitingMenuChoice() {
        return awaitingMenuChoice;
    }

    public void setAwaitingMenuChoice(boolean awaitingMenuChoice) {
        this.awaitingMenuChoice = awaitingMenuChoice;
    }

    // Give waiting orders to free chefs, scaling team size to order size.
    public synchronized void dispatchPendingOrders() {
        if (closed) return;

        while (true) {
            Chef firstChef = findNextAvailableChef();
            if (firstChef == null) break;

            Order order = orderManager.getNextOrder();
            if (order == null) break;

            int desiredChefs = order.getRecommendedChefCount();
            List<Chef> team = new ArrayList<>();
            team.add(firstChef);
            firstChef.setAvailable(false);

            while (team.size() < desiredChefs) {
                Chef extraChef = findNextAvailableChef();
                if (extraChef == null) break;
                extraChef.setAvailable(false);
                team.add(extraChef);
            }

            List<List<OrderItem>> slices = order.distributeItemsAcrossChefs(team.size());
            // Trim team if total quantity was smaller than team.size()
            while (team.size() > slices.size()) {
                Chef unused = team.remove(team.size() - 1);
                unused.setAvailable(true);
            }

            long now = System.currentTimeMillis();
            order.setStatus(Order.Status.PREPARING);
            order.setAssignedChefId(firstChef.getChefId());
            order.initializeTeam(team.size());

            List<String> sliceSummaries = new ArrayList<>();
            List<Integer> slicePreps = new ArrayList<>();
            for (int i = 0; i < team.size(); i++) {
                Chef chef = team.get(i);
                List<OrderItem> slice = slices.get(i);
                String summary = Order.formatItemList(slice);
                int prepSec = Order.calculateSlicePrepSeconds(slice);
                sliceSummaries.add(summary);
                slicePreps.add(prepSec);

                chef.setCurrentOrderId(order.getOrderId());
                chef.setCurrentTaskSummary(summary);
                chef.setExpectedFreeTimeMs(now + prepSec * 1000L);
            }

            synchronized (CONSOLE_LOCK) {
                if (team.size() == 1) {
                    System.out.println("\n[KITCHEN]  " + team.get(0).getChefName()
                            + " started ORD" + order.getOrderId()
                            + " (" + order.getDetailedTypeString() + ") - "
                            + sliceSummaries.get(0) + " (~" + slicePreps.get(0) + "s)");
                } else {
                    System.out.println("\n[KITCHEN]  ORD" + order.getOrderId()
                            + " (" + order.getDetailedTypeString() + ") divided across "
                            + team.size() + " chefs:");
                    for (int i = 0; i < team.size(); i++) {
                        System.out.println("           - " + team.get(i).getChefName()
                                + " -> " + sliceSummaries.get(i)
                                + " (~" + slicePreps.get(i) + "s)");
                    }
                }
                if (!closed && awaitingMenuChoice) {
                    System.out.print("Enter your choice: ");
                }
            }

            boolean isTeamOrder = team.size() > 1;
            for (int i = 0; i < team.size(); i++) {
                kitchen.submit(new ChefWorker(
                        team.get(i), order, sliceSummaries.get(i), slicePreps.get(i), isTeamOrder, this, dbHandler));
            }
        }
    }

    private Chef findNextAvailableChef() {
        Chef best = null;
        int n = chefs.size();
        for (int offset = 0; offset < n; offset++) {
            int idx = (nextChefIndex + offset) % n;
            Chef candidate = chefs.get(idx);
            if (!candidate.isAvailable()) continue;
            if (best == null || candidate.getCompletedOrders() < best.getCompletedOrders()) {
                best = candidate;
            }
        }
        if (best != null) {
            nextChefIndex = (chefs.indexOf(best) + 1) % n;
        }
        return best;
    }

    public synchronized void chefFinished(Chef chef) {
        chef.setAvailable(true);
        chef.setCurrentOrderId(-1);
        chef.setCurrentTaskSummary("");
        chef.setExpectedFreeTimeMs(0);
        if (!closed) {
            dispatchPendingOrders();
        }
    }

    public synchronized void displayChefStatus() {
        System.out.println("\n--------------- KITCHEN STATUS ---------------");
        for (Chef chef : chefs) {
            int activeOrderId = chef.getCurrentOrderId();
            String task = chef.getCurrentTaskSummary();
            String status;
            if (chef.isAvailable()) {
                status = "AVAILABLE";
            } else if (activeOrderId > 0 && !task.isEmpty()) {
                status = "ORD" + activeOrderId + ": " + task;
            } else if (activeOrderId > 0) {
                status = "PREPARING (ORD" + activeOrderId + ")";
            } else {
                status = "PREPARING";
            }
            System.out.printf("%-10s | %-28s | Completed: %d%n",
                    chef.getChefName(), status, chef.getCompletedOrders());
        }
        System.out.println("----------------------------------------------");
    }

    public synchronized List<Chef> getChefs() {
        return new ArrayList<>(chefs);
    }

    private synchronized void closeKitchen() {
        closed = true;
        agingMonitor.shutdownNow();
        kitchen.shutdown();
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

        closeKitchen();
        try {
            if (!kitchen.awaitTermination(120, TimeUnit.SECONDS)) {
                kitchen.shutdownNow();
            }
        } catch (InterruptedException e) {
            kitchen.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
