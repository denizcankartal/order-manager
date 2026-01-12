package com.ordermanager;

import com.ordermanager.cli.OrderManagerCLI;
import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.config.AppConfig;
import com.ordermanager.exception.ConfigurationException;
import com.ordermanager.model.Order;
import com.ordermanager.persistence.StatePersistence;
import com.ordermanager.service.AsyncStatePersister;
import com.ordermanager.service.BalanceService;
import com.ordermanager.service.ExchangeInfoService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.StateManager;
import com.ordermanager.service.TimeSync;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Map;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        StatePersistence persistence = null;
        AsyncStatePersister statePersister = null;
        BinanceRestClient restClient = null;

        try {
            AppConfig config = AppConfig.loadFromEnv();
            logger.info("Configuration loaded: baseUrl={}", config.getBaseUrl());

            TimeSync timeSync = new TimeSync(new OkHttpClient(), config.getBaseUrl());
            timeSync.sync();
            logger.debug("Time synchronized with exchange");

            restClient = new BinanceRestClient(config, timeSync);

            BalanceService balanceService = new BalanceService(restClient);
            ExchangeInfoService exchangeInfoService = new ExchangeInfoService(restClient);

            persistence = new StatePersistence();
            StateManager stateManager = new StateManager();

            try {
                Map<String, Order> savedOrders = persistence.load();
                stateManager.loadState(savedOrders);
                logger.info("Loaded {} orders from disk", savedOrders.size());
            } catch (IOException e) {
                logger.warn("Could not load previous orders, starting with empty state: {}", e.getMessage());
            }

            statePersister = new AsyncStatePersister(persistence);
            statePersister.start();

            OrderService orderService = new OrderService(restClient, stateManager, statePersister, exchangeInfoService);

            int exitCode = new CommandLine(new OrderManagerCLI(balanceService, orderService, exchangeInfoService))
                    .execute(args);

            logger.debug("Command completed with exit code: {}", exitCode);
            statePersister.shutdown(5);
            restClient.shutdown();

            System.exit(exitCode);

        } catch (ConfigurationException e) {
            System.err.println("Configuration error: " + e.getMessage());
            logger.error("Configuration error", e);
            System.exit(1);

        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            logger.error("Fatal error", e);

            if (statePersister != null) {
                try {
                    statePersister.shutdown(5);
                } catch (Exception cleanupError) {
                    logger.error("Error during emergency cleanup", cleanupError);
                }
            }
            if (restClient != null) {
                try {
                    restClient.shutdown();
                } catch (Exception cleanupError) {
                    logger.error("Error during emergency cleanup", cleanupError);
                }
            }

            System.exit(1);
        }
    }
}
