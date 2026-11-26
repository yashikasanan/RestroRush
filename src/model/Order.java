package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {
    public enum Status { PENDING, PREPARING, COMPLETED, CANCELLED }

    public static final int PRIORITY_ADD_ON = 0;   // Mid-meal add-on for eating customer (Highest)
    public static final int PRIORITY_DINE_IN = 1;  // Regular Dine-In (High)
    public static final int PRIORITY_TAKEAWAY = 2; // Takeaway (Normal)

    private final int orderId;
    private final String customerName;
    private final List<OrderItem> items;
    private final int priority; // 0 = Add-On (Urgent), 1 = Dine-In (High), 2 = Takeaway (Normal)
    private final int parentOrderId; // -1 for standalone orders; original orderId for add-on orders
    private final LocalDateTime orderTime;
    private final long createdAtMs;
    private volatile int effectivePriority;
    private volatile boolean promotedByAging;
    private volatile Status status;
    private volatile int assignedChefId;
    private int remainingChefs;
    private final List<String> assignedChefNames = new ArrayList<>();

    public Order(int orderId, String customerName, List<OrderItem> items, int priority) {
        this(orderId, customerName, items, priority, -1);
    }

    public Order(int orderId, String customerName, List<OrderItem> items, int priority, int parentOrderId) {
        if (customerName == null || customerName.trim().isEmpty() || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Customer name and at least one order item are required.");
        }
        if (priority != PRIORITY_ADD_ON && priority != PRIORITY_DINE_IN && priority != PRIORITY_TAKEAWAY) {
            throw new IllegalArgumentException("Priority must be 0 (Add-On), 1 (Dine-In), or 2 (Takeaway).");
        }
        if (priority == PRIORITY_ADD_ON && parentOrderId <= 0) {
            throw new IllegalArgumentException("Add-On orders must reference a valid existing order ID.");
        }
        this.orderId = orderId;
        this.customerName = customerName.trim();
        this.items = new ArrayList<>(items);
        this.priority = priority;
        this.effectivePriority = priority;
        this.promotedByAging = false;
        this.parentOrderId = parentOrderId;
        this.orderTime = LocalDateTime.now();
        this.createdAtMs = System.currentTimeMillis();
        this.status = Status.PENDING;
        this.assignedChefId = -1;
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public int getPriority() { return priority; }
    public int getEffectivePriority() { return effectivePriority; }
    public boolean isPromotedByAging() { return promotedByAging; }
    public long getCreatedAtMs() { return createdAtMs; }
    public int getParentOrderId() { return parentOrderId; }
    public boolean isAddOn() { return priority == PRIORITY_ADD_ON; }
    public boolean isDineInSession() { return priority == PRIORITY_DINE_IN || priority == PRIORITY_ADD_ON; }

    public synchronized boolean promoteByAging() {
        if (effectivePriority > PRIORITY_ADD_ON) {
            effectivePriority--;
            promotedByAging = true;
            return true;
        }
        return false;
    }

    public int getTotalQuantity() {
        int total = 0;
        for (OrderItem item : items) total += item.getQuantity();
        return total;
    }

    /**
     * Determines how many chefs should work on this order based on item count:
     * 1-3 items -> 1 chef, 4-7 -> 2 chefs, 8-12 -> 3 chefs, 13+ -> 4 chefs.
     */
    public int getRecommendedChefCount() {
        int qty = getTotalQuantity();
        if (qty <= 3) return 1;
        if (qty <= 7) return 2;
        if (qty <= 12) return 3;
        return 4;
    }

    public int getParallelPrepTimeSeconds(int chefCount) {
        int seq = getPreparationTimeSeconds();
        if (chefCount <= 1) return seq;
        return Math.max(5, (int) Math.ceil((double) seq / chefCount));
    }

    /**
     * Distributes this order's dishes and quantities across {@code teamSize} chefs.
     * Each chef receives a non-empty list of OrderItems to prepare.
     */
    public List<List<OrderItem>> distributeItemsAcrossChefs(int teamSize) {
        int count = Math.max(1, Math.min(teamSize, getTotalQuantity()));
        List<List<OrderItem>> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            buckets.add(new ArrayList<>());
        }

        if (count == 1) {
            buckets.get(0).addAll(items);
            return buckets;
        }

        // If there are at least as many dish lines as chefs, assign whole dish lines first
        if (items.size() >= count) {
            for (int i = 0; i < items.size(); i++) {
                buckets.get(i % count).add(items.get(i));
            }
            return buckets;
        }

        // Fewer dish lines than chefs (e.g., 14 x Iced Tea for 4 chefs):
        // split quantities evenly across chefs so every chef cooks real portions.
        List<OrderItem> flatUnits = new ArrayList<>();
        for (OrderItem item : items) {
            for (int q = 0; q < item.getQuantity(); q++) {
                flatUnits.add(new OrderItem(item.getDish(), 1));
            }
        }

        int basePerChef = flatUnits.size() / count;
        int extra = flatUnits.size() % count;
        int cursor = 0;
        for (int c = 0; c < count; c++) {
            int take = basePerChef + (c < extra ? 1 : 0);
            List<OrderItem> slice = buckets.get(c);
            for (int t = 0; t < take; t++) {
                OrderItem unit = flatUnits.get(cursor++);
                addOrMergeItem(slice, unit.getDish(), 1);
            }
        }
        return buckets;
    }

    private static void addOrMergeItem(List<OrderItem> list, Dish dish, int qty) {
        for (int i = 0; i < list.size(); i++) {
            OrderItem existing = list.get(i);
            if (existing.getDish().getDishId() == dish.getDishId()) {
                list.set(i, new OrderItem(dish, existing.getQuantity() + qty));
                return;
            }
        }
        list.add(new OrderItem(dish, qty));
    }

    public static int calculateSlicePrepSeconds(List<OrderItem> slice) {
        if (slice == null || slice.isEmpty()) return 1;
        int maxPrep = 1;
        int extraOverhead = 0;
        for (OrderItem item : slice) {
            int p = item.getPreparationTimeSeconds();
            if (p > maxPrep) maxPrep = p;
            extraOverhead += Math.max(1, p / 2);
        }
        extraOverhead -= Math.max(1, maxPrep / 2);
        return maxPrep + Math.max(0, extraOverhead);
    }

    public static String formatItemList(List<OrderItem> slice) {
        StringBuilder sb = new StringBuilder();
        for (OrderItem item : slice) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item.getQuantity()).append(" x ").append(item.getDish().getDishName());
        }
        return sb.toString();
    }

    public synchronized void initializeTeam(int chefCount) {
        this.remainingChefs = chefCount;
        this.assignedChefNames.clear();
    }

    public synchronized boolean markChefCompleted(String chefName) {
        if (chefName != null && !assignedChefNames.contains(chefName)) {
            assignedChefNames.add(chefName);
        }
        if (remainingChefs > 0) {
            remainingChefs--;
        }
        return remainingChefs <= 0;
    }

    public synchronized String getAssignedChefsSummary() {
        if (assignedChefNames.isEmpty()) {
            return assignedChefId > 0 ? "Chef " + assignedChefId : "Chef";
        }
        return String.join(", ", assignedChefNames);
    }

    public String getOrderTypeString() {
        switch (priority) {
            case PRIORITY_ADD_ON: return "Add-On";
            case PRIORITY_DINE_IN: return "Dine-In";
            default: return "Takeaway";
        }
    }

    public String getDetailedTypeString() {
        return isAddOn() ? "Add-On for ORD" + parentOrderId : getOrderTypeString();
    }
    public LocalDateTime getOrderTime() { return orderTime; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getAssignedChefId() { return assignedChefId; }
    public void setAssignedChefId(int assignedChefId) { this.assignedChefId = assignedChefId; }

    public double calculateTotal() {
        double total = 0;
        for (OrderItem item : items) total += item.getSubtotal();
        return total;
    }

    // Calculates realistic preparation time if one chef prepares all items sequentially/batched.
    public int getPreparationTimeSeconds() {
        int maxPrep = 1;
        int extraItemOverhead = 0;
        for (OrderItem item : items) {
            int itemPrep = item.getPreparationTimeSeconds();
            if (itemPrep > maxPrep) {
                maxPrep = itemPrep;
            }
            extraItemOverhead += Math.max(1, itemPrep / 2);
        }
        // Avoid double-counting the longest dish's own half-overhead
        extraItemOverhead -= Math.max(1, maxPrep / 2);
        return maxPrep + Math.max(0, extraItemOverhead);
    }

    public String getDishSummary() {
        StringBuilder result = new StringBuilder();
        for (OrderItem item : items) {
            if (result.length() > 0) result.append(", ");
            result.append(item.getQuantity()).append(" x ").append(item.getDish().getDishName());
        }
        return result.toString();
    }
}
