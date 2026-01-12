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

    @Option(names = { "--symbol" }, description = "Filter by symbol (e.g., BTCUSDT)")
    private String symbol;

    @Override
    public Integer call() {
        try {
            List<Order> orders = parent.getOrderService().listOpenOrders(symbol);

            if (orders.isEmpty()) {
                System.out.println("No open orders" +
                        (symbol != null ? " for symbol " + symbol : "") + ".");
                return 0;
            }

            System.out.printf("%-12s %-18s %-6s %-12s %-14s %-14s %-18s%n",
                    "ORDER_ID", "CLIENT_ID", "SIDE", "SYMBOL",
                    "PRICE", "EXEC_QTY", "UPDATE_TIME");

            System.out.println(
                    "----------------------------------------------------------------------------------------");

            for (Order o : orders) {
                long updateTime = o.getUpdateTime();
                Instant instant = Instant.ofEpochMilli(updateTime);
                String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(instant);

                System.out.printf("%-12s %-18s %-6s %-12s %-14s %-14s %-18s%n",
                        o.getOrderId(),
                        o.getClientOrderId(),
                        o.getSide(),
                        o.getSymbol(),
                        o.getPrice(),
                        o.getExecutedQty(),
                        formatted);

            }

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to list orders: " + e.getMessage());
            return 1;
        }
    }
}
