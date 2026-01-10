package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command to list open orders
 */
@Command(name = "list", description = "List open orders (optionally filtered by symbol)")
public class ListOrdersCommand implements Callable<Integer> {

    @Option(names = { "--symbol" }, description = "Filter by symbol (e.g., BTCUSDT)")
    private String symbol;

    @Override
    public Integer call() throws Exception {
        // TODO: IMPLETMENT THIS LATER
        System.out.println("List orders command");
        if (symbol != null) {
            System.out.println("Symbol filter: " + symbol);
        }
        return 0;
    }
}
