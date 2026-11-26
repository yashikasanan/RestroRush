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
    private volatile Status status;
    private volatile int assignedChefId;

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
        this.parentOrderId = parentOrderId;
        this.orderTime = LocalDateTime.now();
        this.status = Status.PENDING;
        this.assignedChefId = -1;
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public int getPriority() { return priority; }
    public int getParentOrderId() { return parentOrderId; }
    public boolean isAddOn() { return priority == PRIORITY_ADD_ON; }
    public boolean isDineInSession() { return priority == PRIORITY_DINE_IN || priority == PRIORITY_ADD_ON; }

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

    // One chef prepares the complete order; use the longest dish time as the simulation duration.
    public int getPreparationTimeSeconds() {
        int longest = 1;
        for (OrderItem item : items) {
            longest = Math.max(longest, item.getDish().getPreparationTimeSeconds());
        }
        return longest;
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
