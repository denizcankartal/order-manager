package com.ordermanager.cli;

import com.ordermanager.model.Order;
import com.ordermanager.model.OrderSide;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

@Command(name = "add", description = "Place a new LIMIT order")
public class AddOrderCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Option(names = { "--side" }, required = true, description = "Order side: BUY or SELL")
    private OrderSide side;

    @Option(names = { "--price" }, required = true, description = "Limit price")
    private BigDecimal price;

    @Option(names = { "--qty" }, required = true, description = "Order quantity")
    private BigDecimal quantity;

    @Option(names = { "--client-id" }, description = "Client order ID (optional, auto-generated if not provided)")
    private String clientOrderId;

    // visible for testing
    void setParent(OrderManagerCLI parent) {
        this.parent = parent;
    }

    @Override
    public Integer call() {
        try {
            parent.configureLogging();

            var result = parent.getOrderService().placeOrder(parent.getSymbol(), side, price, quantity, clientOrderId);

            Order order = result.getOrder();

            if (!result.getWarnings().isEmpty()) {
                System.out.println("Warnings:");
                result.getWarnings().forEach(w -> System.out.println(" - " + w));
            }

            System.out.printf(
                    "Order placed: id=%s clientId=%s side=%s %s %s @ %s status=%s%n",
                    order.getOrderId(),
                    order.getClientOrderId(),
                    order.getSide(),
                    order.getOrigQty(),
                    order.getSymbol(),
                    order.getPrice(),
                    order.getStatus());

            System.out.printf("{\"orderId\": %s, \"clientOrderId\": \"%s\", \"status\": \"%s\"}%n",
                    order.getOrderId(),
                    order.getClientOrderId(),
                    order.getStatus());

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to place order: " + e.getMessage());
            return 1;
        }
    }
}
