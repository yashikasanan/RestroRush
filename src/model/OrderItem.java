package model;

public class OrderItem {
    private final Dish dish;
    private final int quantity;

    public OrderItem(Dish dish, int quantity) {
        if (dish == null || quantity <= 0) {
            throw new IllegalArgumentException("Dish and a positive quantity are required.");
        }
        this.dish = dish;
        this.quantity = quantity;
    }

    public Dish getDish() { return dish; }
    public int getQuantity() { return quantity; }
    public double getSubtotal() { return dish.getPrice() * quantity; }

    /**
     * Calculates realistic batch preparation time for this item's quantity:
     * first portion takes full base time, additional portions add batched prep time.
     */
    public int getPreparationTimeSeconds() {
        return calculateBatchPrepSeconds(dish, quantity);
    }

    public static int calculateBatchPrepSeconds(Dish dish, int qty) {
        int base = dish.getPreparationTimeSeconds();
        if (qty <= 1) return base;
        return base + (int) Math.ceil((qty - 1) * 0.5);
    }
}
