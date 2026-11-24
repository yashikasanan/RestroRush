package model;

public class Dish {
    private final int dishId;
    private final String dishName;
    private final double price;
    private final int preparationTimeSeconds;
    private int availableQuantity;

    public Dish(int dishId, String dishName, double price,
                int preparationTimeSeconds, int availableQuantity) {
        this.dishId = dishId;
        this.dishName = dishName;
        this.price = price;
        this.preparationTimeSeconds = preparationTimeSeconds;
        this.availableQuantity = availableQuantity;
    }

    public int getDishId() { return dishId; }
    public String getDishName() { return dishName; }
    public double getPrice() { return price; }
    public int getPreparationTimeSeconds() { return preparationTimeSeconds; }
    public synchronized int getAvailableQuantity() { return availableQuantity; }

    public synchronized boolean hasQuantity(int quantity) {
        return quantity > 0 && availableQuantity >= quantity;
    }

    public synchronized boolean reduceQuantity(int quantity) {
        if (!hasQuantity(quantity)) return false;
        availableQuantity -= quantity;
        return true;
    }

    public synchronized void addQuantity(int quantity) {
        if (quantity > 0) availableQuantity += quantity;
    }
}
