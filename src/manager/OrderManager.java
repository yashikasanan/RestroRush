package manager;

import model.Order;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class OrderManager {
    private final PriorityQueue<Order> pendingOrders = new PriorityQueue<>(
        Comparator.comparingInt(Order::getPriority)
                  .thenComparing(Order::getOrderTime)
                  .thenComparingInt(Order::getOrderId)
    );
    private final List<Order> allOrders = new ArrayList<>();
    private int nextOrderId = 101;

    public synchronized int generateOrderId() {
        return nextOrderId++;
    }

    public synchronized void placeOrder(Order order) {
        pendingOrders.offer(order);
        allOrders.add(order);
    }

    public synchronized Order getNextOrder() {
        return pendingOrders.poll();
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
        return new ArrayList<>(pendingOrders);
    }

    public synchronized void displayAllOrders() {
        System.out.println("\n---------------- ALL ORDERS ----------------");
        if (allOrders.isEmpty()) {
            System.out.println("No orders have been placed yet.");
        }
        for (Order order : allOrders) {
            System.out.printf("ORD%d | %-12s | %-10s | %-10s | Rs. %.2f | %s%n",
                    order.getOrderId(), order.getCustomerName(), order.getOrderTypeString(),
                    order.getStatus(), order.calculateTotal(), order.getDishSummary());
        }
        System.out.println("--------------------------------------------");
    }
}
