package com.ordermanager.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI command with subcommands
 * 
 * add: Place a new LIMIT order
 * cancel: Cancel an existing order
 * list: List open orders
 * show: Show order details
 * balances: Show account balances
 */
@Command(name = "order_manager", description = "Binance Spot Order Manager CLI", version = "1.0.0", mixinStandardHelpOptions = true, subcommands = {
        BalancesCommand.class,
        AddOrderCommand.class,
        CancelOrderCommand.class,
        ListOrdersCommand.class,
        ShowOrderCommand.class
})
public class OrderManagerCLI implements Runnable {

    @Override
    public void run() {
        // Show help when no subcommand is provided
        CommandLine.usage(this, System.out);
    }
}
