package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {
    public enum Status { PENDING, PREPARING, COMPLETED, CANCELLED }

    private final int orderId;
    private final String customerName;
    private final List<OrderItem> items;
    private final int priority; // 1 = Dine-In (High), 2 = Takeaway (Normal)
    private final LocalDateTime orderTime;
    private volatile Status status;
    private volatile int assignedChefId;

    public Order(int orderId, String customerName, List<OrderItem> items, int priority) {
        if (customerName == null || customerName.trim().isEmpty() || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Customer name and at least one order item are required.");
        }
        if (priority != 1 && priority != 2) {
            throw new IllegalArgumentException("Priority must be 1 (Dine-In / High) or 2 (Takeaway / Normal).");
        }
        this.orderId = orderId;
        this.customerName = customerName.trim();
        this.items = new ArrayList<>(items);
        this.priority = priority;
        this.orderTime = LocalDateTime.now();
        this.status = Status.PENDING;
        this.assignedChefId = -1;
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public int getPriority() { return priority; }
    public String getOrderTypeString() { return priority == 1 ? "Dine-In" : "Takeaway"; }
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
