package com.ordermanager.cli;

import com.ordermanager.model.OrderSide;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

/**
 * Command to place a new LIMIT order
 */
@Command(name = "add", description = "Place a new LIMIT order")
public class AddOrderCommand implements Callable<Integer> {

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
    public Integer call() throws Exception {
        // TODO: IMPLMENT THIS LATER
        System.out.println("Add order command - implementation pending");
        System.out.println("Side: " + side);
        System.out.println("Symbol: " + symbol);
        System.out.println("Price: " + price);
        System.out.println("Quantity: " + quantity);
        if (clientOrderId != null) {
            System.out.println("Client Order ID: " + clientOrderId);
        }
        return 0;
    }
}
