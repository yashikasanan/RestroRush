package model;

public class Chef {
    private final int chefId;
    private final String chefName;
    private volatile boolean available;
    private int completedOrders;

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
}
