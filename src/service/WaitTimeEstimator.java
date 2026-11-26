package service;

import manager.ChefManager;
import manager.OrderManager;
import model.Chef;
import model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates how long a customer will wait for their order based on
 * current kitchen workload: busy chefs, pending queue depth, and dish prep times.
 *
 * REAL-WORLD PROBLEM SOLVED:
 * "How long will my food take?" — Studies show 60-70% of restaurant customers
 * abandon orders or leave when wait times are unknown or too long.
 * Food delivery apps (Swiggy, Zomato, Uber Eats) all prominently display
 * estimated delivery/preparation times for exactly this reason.
 *
 * HOW IT WORKS:
 * 1. Checks which chefs are busy and when each will be free.
 * 2. Looks at all pending orders ahead in the priority queue.
 * 3. Simulates assigning each pending order to the earliest-free chef.
 * 4. Determines when the target order would start and finish cooking.
 * 5. Returns the total estimated wait in seconds.
 */
public class WaitTimeEstimator {

    private final ChefManager chefManager;
    private final OrderManager orderManager;

    public WaitTimeEstimator(ChefManager chefManager, OrderManager orderManager) {
        this.chefManager = chefManager;
        this.orderManager = orderManager;
    }

    /**
     * Estimates the number of seconds until a specific order (already in the queue)
     * will be fully prepared, based on current chef workload and queue position.
     */
    public int estimateWaitSeconds(Order targetOrder) {
        if (targetOrder.getStatus() == Order.Status.COMPLETED
                || targetOrder.getStatus() == Order.Status.CANCELLED) {
            return 0;
        }

        long now = System.currentTimeMillis();
        if (targetOrder.getStatus() == Order.Status.PREPARING) {
            for (Chef chef : chefManager.getChefs()) {
                if (chef.getChefId() == targetOrder.getAssignedChefId()) {
                    long freeAt = chef.getExpectedFreeTimeMs();
                    if (freeAt > now) {
                        return (int) ((freeAt - now + 999) / 1000);
                    }
                    return targetOrder.getPreparationTimeSeconds();
                }
            }
            return targetOrder.getPreparationTimeSeconds();
        }

        List<Long> chefFreeTimes = buildChefFreeTimeList(now);
        List<Order> pending = getSortedPendingOrders();

        long targetCompletion = now;
        boolean foundInPending = false;

        for (Order order : pending) {
            int earliest = findEarliestFreeChef(chefFreeTimes);
            int teamSize = Math.min(order.getRecommendedChefCount(), chefFreeTimes.size());
            long completion = chefFreeTimes.get(earliest)
                    + order.getParallelPrepTimeSeconds(teamSize) * 1000L;
            chefFreeTimes.set(earliest, completion);

            if (order.getOrderId() == targetOrder.getOrderId()) {
                targetCompletion = completion;
                foundInPending = true;
                break;
            }
        }

        if (!foundInPending) {
            return targetOrder.getPreparationTimeSeconds();
        }

        return Math.max(0, (int) ((targetCompletion - now) / 1000));
    }

    /**
     * Displays estimated wait times for all orders currently in the kitchen
     * (both PREPARING and PENDING).
     */
    public void displayCurrentWaitTimes() {
        long now = System.currentTimeMillis();

        List<Order> allOrders = orderManager.getAllOrders();
        List<Order> preparingOrders = new ArrayList<>();
        for (Order o : allOrders) {
            if (o.getStatus() == Order.Status.PREPARING) {
                preparingOrders.add(o);
            }
        }

        List<Order> pendingOrders = getSortedPendingOrders();

        if (preparingOrders.isEmpty() && pendingOrders.isEmpty()) {
            System.out.println("\nNo active orders. Kitchen is idle — zero wait time!");
            return;
        }

        System.out.println("\n=============== ESTIMATED WAIT TIMES ===============");
        System.out.printf("  %-8s %-13s %-9s %-10s %s%n",
                "Order", "Customer", "Type", "Status", "Wait");
        System.out.println("----------------------------------------------------");

        // Show currently preparing orders with remaining time
        for (Order o : preparingOrders) {
            long remaining = 0;
            for (Chef chef : chefManager.getChefs()) {
                if (chef.getChefId() == o.getAssignedChefId()) {
                    long diffMs = chef.getExpectedFreeTimeMs() - now;
                    remaining = diffMs > 0 ? (diffMs + 999) / 1000 : 0;
                    break;
                }
            }
            String waitLabel = remaining > 0 ? "~" + formatTime((int) remaining) + " left" : "finishing now";
            System.out.printf("  %-8s %-13s %-9s %-10s %s%n",
                    "ORD" + o.getOrderId(), o.getCustomerName(),
                    o.getOrderTypeString(), "COOKING",
                    waitLabel);
        }

        // Simulate and show pending order wait times
        if (!pendingOrders.isEmpty()) {
            List<Long> chefFreeTimes = buildChefFreeTimeList(now);

            for (Order order : pendingOrders) {
                int earliest = findEarliestFreeChef(chefFreeTimes);
                int teamSize = Math.min(order.getRecommendedChefCount(), chefFreeTimes.size());
                long completion = chefFreeTimes.get(earliest)
                        + order.getParallelPrepTimeSeconds(teamSize) * 1000L;
                chefFreeTimes.set(earliest, completion);

                int waitSec = (int) ((completion - now + 999) / 1000);
                System.out.printf("  %-8s %-13s %-9s %-10s ~%s%n",
                        "ORD" + order.getOrderId(), order.getCustomerName(),
                        order.getOrderTypeString(), "QUEUED",
                        formatTime(waitSec));
            }
        }

        System.out.println("====================================================");
    }

    // -------------------- Helper Methods --------------------

    private List<Long> buildChefFreeTimeList(long now) {
        List<Long> freeTimes = new ArrayList<>();
        for (Chef chef : chefManager.getChefs()) {
            if (chef.isAvailable()) {
                freeTimes.add(now);
            } else {
                freeTimes.add(Math.max(chef.getExpectedFreeTimeMs(), now));
            }
        }
        return freeTimes;
    }

    private List<Order> getSortedPendingOrders() {
        List<Order> pending = orderManager.getPendingOrders();
        pending.sort(OrderManager.ORDER_PRIORITY_COMPARATOR);
        return pending;
    }

    private int findEarliestFreeChef(List<Long> chefFreeTimes) {
        int earliest = 0;
        for (int i = 1; i < chefFreeTimes.size(); i++) {
            if (chefFreeTimes.get(i) < chefFreeTimes.get(earliest)) {
                earliest = i;
            }
        }
        return earliest;
    }

    /**
     * Formats seconds into a human-readable string like "2 min 30 sec".
     */
    public static String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "ready soon";
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes == 0) return seconds + " sec";
        if (seconds == 0) return minutes + " min";
        return minutes + " min " + seconds + " sec";
    }
}
