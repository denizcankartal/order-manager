package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

import com.ordermanager.model.Balance;

@Command(name = "balances", description = "Print free/locked balances for relevant assets")
public class BalancesCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Override
    public Integer call() {
        try {
            List<Balance> balances = parent.getBalanceService().getNonZeroBalances();

            System.out.printf("%-10s %-20s %-20s %-20s%n",
                    "ASSET", "FREE", "LOCKED", "TOTAL");
            System.out.println("---------------------------------------------------------------------");

            for (Balance b : balances) {
                System.out.printf("%-10s %-20s %-20s %-20s%n",
                        b.getAsset(),
                        b.getFree(),
                        b.getLocked(),
                        b.getTotal());
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to retrieve balances: " + e.getMessage());
            return 1;
        }
    }
}
