package com.ordermanager.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(name = "stream", description = "Stream user data updates (execution reports)")
public class StreamCommand implements Callable<Integer> {
    @ParentCommand
    private OrderManagerCLI parent;

    @Override
    public Integer call() {
        try {
            parent.configureLogging();
            if (parent.getUserDataStreamService() == null) {
                System.err.println("User data stream service is not available.");
                return 1;
            }

            parent.getOrderService().refreshOpenOrders();
            parent.getUserDataStreamService().start();
            System.out.println("User data stream started. Press Ctrl+C to stop.");

            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                parent.getUserDataStreamService().stop();
                latch.countDown();
            }));

            latch.await();
            return 0;

        } catch (Exception e) {
            System.err.println("Failed to start user data stream: " + e.getMessage());
            return 1;
        }
    }
}
