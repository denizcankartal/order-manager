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

    @Option(names = { "--symbol" }, required = true, description = "Trading symbol (e.g., BTCUSDT)")
    private String symbol;

    @Option(names = { "--price" }, required = true, description = "Limit price")
    private BigDecimal price;

    @Option(names = { "--qty" }, required = true, description = "Order quantity")
    private BigDecimal quantity;

    @Option(names = { "--client-id" }, description = "Client order ID (optional, auto-generated if not provided)")
    private String clientOrderId;

    @Override
    public Integer call() {
        try {
            var result = parent.getOrderService().placeOrder(symbol, side, price, quantity, clientOrderId);
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

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to place order: " + e.getMessage());
            return 1;
        }
    }
}
