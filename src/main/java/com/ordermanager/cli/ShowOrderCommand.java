package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

import com.ordermanager.model.Order;

@Command(name = "show", description = "Show detailed information for a specific order")
public class ShowOrderCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Option(names = { "--id" }, required = true, description = "Order ID or client order ID")
    private String orderId;

    @Override
    public Integer call() {
        try {
            parent.configureLogging();
            Order order = parent.getOrderService().fetchAndUpdateOrder(orderId, parent.getSymbol());

            System.out.println("{");
            System.out.printf("  \"orderId\": %d,%n", order.getOrderId());
            System.out.printf("  \"clientOrderId\": \"%s\",%n", order.getClientOrderId());
            System.out.printf("  \"symbol\": \"%s\",%n", order.getSymbol());
            System.out.printf("  \"side\": \"%s\",%n", order.getSide());
            System.out.printf("  \"price\": \"%s\",%n", order.getPrice());
            System.out.printf("  \"origQty\": \"%s\",%n", order.getOrigQty());
            System.out.printf("  \"executedQty\": \"%s\",%n", order.getExecutedQty());
            System.out.printf("  \"status\": \"%s\",%n", order.getStatus());
            System.out.printf("  \"updateTime\": %d%n", order.getUpdateTime());
            System.out.println("}");
            return 0;

        } catch (Exception e) {
            System.err.println("Failed to show order: " + e.getMessage());
            return 1;
        }
    }
}
