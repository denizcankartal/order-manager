package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

import com.ordermanager.model.Order;

@Command(name = "cancel", description = "Cancel an existing order by orderId or origClientOrderId")
public class CancelOrderCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Option(names = { "--id" }, required = true, description = "Order ID or client order ID")
    private String orderId;

    @Override
    public Integer call() {
        try {
            Order order = parent.getOrderService().cancelOrder(orderId);

            System.out.printf(
                    "Order canceled: id=%s clientId=%s symbol=%s status=%s executedQty=%s%n",
                    order.getOrderId(),
                    order.getClientOrderId(),
                    order.getSymbol(),
                    order.getStatus(),
                    order.getExecutedQty());

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to cancel order: " + e.getMessage());
            return 1;
        }
    }
}
