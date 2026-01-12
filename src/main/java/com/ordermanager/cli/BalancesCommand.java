package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

import com.ordermanager.model.dto.AccountResponse.BalanceInfo;

@Command(name = "balances", description = "Print free/locked balances for relevant assets")
public class BalancesCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Override
    public Integer call() {
        try {
            parent.configureLogging();
            String[] assets = { parent.getBaseAsset(), parent.getQuoteAsset() };
            List<BalanceInfo> balances = parent.getBalanceService().getBalances(assets);

            System.out.printf("%-10s %-20s %-20s%n", "ASSET", "FREE", "LOCKED");
            System.out.println("---------------------------------------------------------------------");

            for (BalanceInfo b : balances) {
                System.out.printf("%-10s %-20s %-20sn",
                        b.getAsset(),
                        b.getFree(),
                        b.getLocked());
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Failed to retrieve balances: " + e.getMessage());
            return 1;
        }
    }
}
