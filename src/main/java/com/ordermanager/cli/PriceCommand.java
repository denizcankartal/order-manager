package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

@Command(name = "price", description = "Get the latest price for a symbol")
public class PriceCommand implements Callable<Integer> {

    @ParentCommand
    private OrderManagerCLI parent;

    @Override
    public Integer call() {
        parent.configureLogging();

        return watchContinuously();
    }

    private Integer watchContinuously() {
        System.out.printf("Watching %s price every %d seconds. Press Ctrl+C to stop.%n",
                parent.getSymbol(), 10);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Stopping price watcher...")));

        try {
            while (true) {
                BigDecimal price = parent.getOrderService().getCurrentPrice(parent.getSymbol());
                long now = System.currentTimeMillis();

                System.out.printf("%d - %s%n", now, price);

                Thread.sleep(10 * 1000L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to retrieve price for symbol '" + parent.getSymbol() + "': " + e.getMessage());
            return 1;
        }
    }
}
