package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

import com.ordermanager.model.Order;

@Command(name = "list", description = "List open orders (optionally filtered by symbol)")
public class ListOrdersCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Override
    public Integer call() {
        try {
            parent.configureLogging();
            List<Order> orders = parent.getOrderService().listOpenOrders(parent.getSymbol());

            if (orders.isEmpty()) {
                System.out.println("No open orders" +
                        (parent.getSymbol() != null ? " for symbol " + parent.getSymbol() : "") + ".");
                return 0;
            }

            System.out.printf("%-12s %-18s %-6s %-12s %-14s %-14s %-14s %-10s %-18s%n",
                    "ORDER_ID", "CLIENT_ID", "SIDE", "SYMBOL",
                    "PRICE", "ORIG_QTY", "EXEC_QTY", "STATUS", "UPDATE_TIME");

            System.out.println(
                    "----------------------------------------------------------------------------------------------------------");

            for (Order o : orders) {
                long updateTime = o.getUpdateTime();
                Instant instant = Instant.ofEpochMilli(updateTime);
                String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(instant);

                System.out.printf("%-12s %-18s %-6s %-12s %-14s %-14s %-14s %-10s %-18s%n",
                        o.getOrderId(),
                        o.getClientOrderId(),
                        o.getSide(),
                        o.getSymbol(),
                        o.getPrice(),
                        o.getOrigQty(),
                        o.getExecutedQty(),
                        o.getStatus(),
                        formatted);

            }

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to list orders: " + e.getMessage());
            return 1;
        }
    }
}
