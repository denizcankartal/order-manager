package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command to cancel an order
 */
@Command(name = "cancel", description = "Cancel an existing order by orderId or origClientOrderId")
public class CancelOrderCommand implements Callable<Integer> {

    @Option(names = { "--id" }, required = true, description = "Order ID or client order ID")
    private String orderId;

    @Override
    public Integer call() throws Exception {
        // TODO: IMPLETEMENT THIS LATER
        System.out.println("Cancel order command");
        System.out.println("Order ID: " + orderId);
        return 0;
    }
}
