package com.ordermanager.cli;

import com.ordermanager.service.BalanceService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.UserDataStreamService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "order_manager", description = "Order Manager CLI", version = "1.0.0", mixinStandardHelpOptions = true, subcommands = {
        BalancesCommand.class,
        AddOrderCommand.class,
        CancelOrderCommand.class,
        ListOrdersCommand.class,
        ShowOrderCommand.class,
        PriceCommand.class
})
public class OrderManagerCLI implements Runnable {

    private final BalanceService balanceService;
    private final OrderService orderService;
    private final UserDataStreamService userDataStreamService;
    private final String baseAsset;
    private final String quoteAsset;

    @CommandLine.Option(names = "--verbose", description = "Enable verbose HTTP logging")
    private boolean verbose;

    public OrderManagerCLI(BalanceService balanceService, OrderService orderService,
            UserDataStreamService userDataStreamService, String baseAsset,
            String quotedAsset) {
        this.balanceService = balanceService;
        this.orderService = orderService;
        this.userDataStreamService = userDataStreamService;
        this.quoteAsset = quotedAsset;
        this.baseAsset = baseAsset;
    }

    public BalanceService getBalanceService() {
        return balanceService;
    }

    public OrderService getOrderService() {
        return orderService;
    }

    public UserDataStreamService getUserDataStreamService() {
        return userDataStreamService;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getQuoteAsset() {
        return quoteAsset;
    }

    public String getBaseAsset() {
        return baseAsset;
    }

    public String getSymbol() {
        return baseAsset + quoteAsset;
    }

    public void configureLogging() {
        if (!verbose) {
            return;
        }

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
