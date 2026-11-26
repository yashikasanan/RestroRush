package model;

public class Chef {
    private final int chefId;
    private final String chefName;
    private volatile boolean available;
    private int completedOrders;
    private volatile long expectedFreeTimeMs;
    private volatile int currentOrderId = -1;
    private volatile String currentTaskSummary = "";

    public Chef(int chefId, String chefName) {
        this.chefId = chefId;
        this.chefName = chefName;
        this.available = true;
        this.completedOrders = 0;
    }

    public int getChefId() { return chefId; }
    public String getChefName() { return chefName; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public synchronized void incrementCompletedOrders() {
        completedOrders++;
    }

    public synchronized int getCompletedOrders() {
        return completedOrders;
    }

    public long getExpectedFreeTimeMs() { return expectedFreeTimeMs; }
    public void setExpectedFreeTimeMs(long timeMs) { this.expectedFreeTimeMs = timeMs; }

    public int getCurrentOrderId() { return currentOrderId; }
    public void setCurrentOrderId(int currentOrderId) { this.currentOrderId = currentOrderId; }

    public String getCurrentTaskSummary() { return currentTaskSummary; }
    public void setCurrentTaskSummary(String summary) { this.currentTaskSummary = summary != null ? summary : ""; }
}
