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
}
