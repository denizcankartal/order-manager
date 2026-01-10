package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command to show detailed order information
 */
@Command(name = "show", description = "Show detailed information for a specific order")
public class ShowOrderCommand implements Callable<Integer> {

    @Option(names = { "--id" }, required = true, description = "Order ID or client order ID")
    private String orderId;

    @Override
    public Integer call() throws Exception {
        // TODO: IMPLMENET LATER
        System.out.println("Show order command");
        System.out.println("Order ID: " + orderId);
        return 0;
    }
}
