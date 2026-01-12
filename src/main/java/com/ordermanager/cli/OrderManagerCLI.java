package com.ordermanager.cli;

import com.ordermanager.service.BalanceService;
import com.ordermanager.service.ExchangeInfoService;
import com.ordermanager.service.OrderService;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "order_manager", description = "Order Manager CLI", version = "1.0.0", mixinStandardHelpOptions = true, subcommands = {
        BalancesCommand.class,
        AddOrderCommand.class,
        CancelOrderCommand.class,
        ListOrdersCommand.class,
        ShowOrderCommand.class
})
public class OrderManagerCLI implements Runnable {

    private final BalanceService balanceService;
    private final OrderService orderService;
    private final ExchangeInfoService exchangeInfoService;

    public OrderManagerCLI(BalanceService balanceService, OrderService orderService,
            ExchangeInfoService exchangeInfoService) {
        this.balanceService = balanceService;
        this.orderService = orderService;
        this.exchangeInfoService = exchangeInfoService;
    }

    public BalanceService getBalanceService() {
        return balanceService;
    }

    public OrderService getOrderService() {
        return orderService;
    }

    public ExchangeInfoService getExchangeInfoService() {
        return exchangeInfoService;
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
