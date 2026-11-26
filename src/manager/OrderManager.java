package manager;

import model.Order;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class OrderManager {
    public static final long TAKEAWAY_AGING_THRESHOLD_MS = 20_000L; // 20 seconds wait promotes Takeaway -> Dine-In
    public static final long DINE_IN_AGING_THRESHOLD_MS = 40_000L;  // 40 seconds wait promotes Dine-In -> Urgent

    public static final Comparator<Order> ORDER_PRIORITY_COMPARATOR =
        Comparator.comparingInt(Order::getEffectivePriority)
                  .thenComparing(Order::getOrderTime)
                  .thenComparingInt(Order::getOrderId);

    private final PriorityQueue<Order> pendingOrders = new PriorityQueue<>(ORDER_PRIORITY_COMPARATOR);
    private final List<Order> allOrders = new ArrayList<>();
    private int nextOrderId = 101;

    public synchronized int generateOrderId() {
        return nextOrderId++;
    }

    public synchronized void placeOrder(Order order) {
        if (order.getStatus() == Order.Status.PENDING) {
            pendingOrders.offer(order);
        }
        allOrders.add(order);
    }

    public synchronized Order getNextOrder() {
        applyPriorityAging();
        return pendingOrders.poll();
    }

    /**
     * Anti-starvation: promotes long-waiting orders so Takeaway orders
     * are never starved indefinitely by Dine-In / Add-On rushes.
     */
    public synchronized int applyPriorityAging() {
        if (pendingOrders.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        List<Order> toPromote = new ArrayList<>();

        for (Order order : pendingOrders) {
            long waitedMs = now - order.getCreatedAtMs();
            if (order.getEffectivePriority() == Order.PRIORITY_TAKEAWAY
                    && waitedMs >= TAKEAWAY_AGING_THRESHOLD_MS) {
                toPromote.add(order);
            } else if (order.getEffectivePriority() == Order.PRIORITY_DINE_IN
                    && waitedMs >= DINE_IN_AGING_THRESHOLD_MS) {
                toPromote.add(order);
            }
        }

        if (toPromote.isEmpty()) return 0;

        for (Order order : toPromote) {
            pendingOrders.remove(order);
            if (order.promoteByAging()) {
                synchronized (ChefManager.CONSOLE_LOCK) {
                    String newTier = order.getEffectivePriority() == Order.PRIORITY_DINE_IN
                            ? "Dine-In Priority" : "Top Priority";
                    System.out.println("\n[ANTI-STARVATION] ORD" + order.getOrderId()
                            + " (" + order.getOrderTypeString() + ") waited "
                            + ((now - order.getCreatedAtMs()) / 1000) + "s -> promoted to " + newTier + "!");
                }
            }
            pendingOrders.offer(order);
        }
        return toPromote.size();
    }

    public synchronized boolean cancelOrder(int orderId) {
        for (Order order : allOrders) {
            if (order.getOrderId() == orderId && order.getStatus() == Order.Status.PENDING) {
                boolean removed = pendingOrders.remove(order);
                if (removed) {
                    order.setStatus(Order.Status.CANCELLED);
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized Order findOrder(int orderId) {
        for (Order order : allOrders) {
            if (order.getOrderId() == orderId) return order;
        }
        return null;
    }

    public synchronized List<Order> getAllOrders() {
        return new ArrayList<>(allOrders);
    }

    public synchronized List<Order> getPendingOrders() {
        applyPriorityAging();
        return new ArrayList<>(pendingOrders);
    }

    public synchronized List<Order> getCompletedDineInOrders() {
        List<Order> eligible = new ArrayList<>();
        for (Order order : allOrders) {
            if (order.getStatus() == Order.Status.COMPLETED && order.isDineInSession()) {
                eligible.add(order);
            }
        }
        return eligible;
    }

    public synchronized boolean hasInProgressDineInOrders() {
        for (Order order : allOrders) {
            if (order.isDineInSession()
                    && (order.getStatus() == Order.Status.PENDING
                        || order.getStatus() == Order.Status.PREPARING)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Order findLatestCompletedDineInOrderByCustomer(String customerName) {
        if (customerName == null || customerName.trim().isEmpty()) return null;
        String normalized = customerName.trim();
        for (int i = allOrders.size() - 1; i >= 0; i--) {
            Order order = allOrders.get(i);
            if (order.getStatus() == Order.Status.COMPLETED
                    && order.isDineInSession()
                    && order.getCustomerName().equalsIgnoreCase(normalized)) {
                return order;
            }
        }
        return null;
    }

    /**
     * Validates whether an existing order qualifies for a mid-meal priority Add-On.
     * Returns null if eligible, or an explanation message if not.
     */
    public synchronized String validateAddOnEligibility(int originalOrderId) {
        Order original = findOrder(originalOrderId);
        if (original == null) {
            return "Order ORD" + originalOrderId + " not found.";
        }
        if (!original.isDineInSession()) {
            return "Order ORD" + originalOrderId + " is a Takeaway order. "
                    + "Add-On priority is only for seated Dine-In customers currently eating.";
        }
        if (original.getStatus() == Order.Status.CANCELLED) {
            return "Order ORD" + originalOrderId + " was cancelled and cannot receive an Add-On.";
        }
        if (original.getStatus() != Order.Status.COMPLETED) {
            return "Order ORD" + originalOrderId + " is still " + original.getStatus()
                    + ". Add-On priority unlocks once the customer's food has arrived (COMPLETED).";
        }
        return null;
    }

    public synchronized int getQueuePosition(int orderId) {
        applyPriorityAging();
        List<Order> sorted = new ArrayList<>(pendingOrders);
        sorted.sort(ORDER_PRIORITY_COMPARATOR);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getOrderId() == orderId) {
                return i + 1;
            }
        }
        return -1;
    }

    public synchronized void displayAllOrders() {
        applyPriorityAging();
        System.out.println("\n---------------- ALL ORDERS ----------------");
        if (allOrders.isEmpty()) {
            System.out.println("No orders have been placed yet.");
        }
        for (Order order : allOrders) {
            StringBuilder summary = new StringBuilder(order.getDishSummary());
            if (order.isAddOn()) {
                summary.append(" [Add-On for ORD").append(order.getParentOrderId()).append("]");
            }
            if (order.isPromotedByAging() && order.getStatus() == Order.Status.PENDING) {
                summary.append(" [Aged Priority]");
            }
            System.out.printf("ORD%d | %-12s | %-10s | %-10s | Rs. %.2f | %s%n",
                    order.getOrderId(), order.getCustomerName(), order.getOrderTypeString(),
                    order.getStatus(), order.calculateTotal(), summary);
        }
        System.out.println("--------------------------------------------");
    }
}
