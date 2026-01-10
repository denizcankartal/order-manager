package com.ordermanager.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Command to display account balances
 */
@Command(name = "balances", description = "Print free/locked balances for relevant assets")
public class BalancesCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        // TODO: Implement later
        System.out.println("Balances command - implementation pending");
        return 0;
    }
}
