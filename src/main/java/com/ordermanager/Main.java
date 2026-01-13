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
import com.ordermanager.service.UserDataStreamService;

import ch.qos.logback.classic.Level;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        StatePersistence persistence = null;
        AsyncStatePersister statePersister = null;
        BinanceRestClient restClient = null;
        UserDataStreamService userDataStreamService = null;

        try {
            configureLogLevel(args);

            AppConfig config = AppConfig.loadFromEnv();
            TimeSync timeSync = new TimeSync(new OkHttpClient(), config.getBaseUrl());
            timeSync.sync();
            restClient = new BinanceRestClient(config, timeSync);

            BalanceService balanceService = new BalanceService(restClient);
            ExchangeInfoService exchangeInfoService = new ExchangeInfoService(restClient);

            persistence = new StatePersistence();
            StateManager stateManager = new StateManager();

            try {
                Map<String, Order> savedOrders = persistence.load();
                stateManager.loadState(savedOrders);
            } catch (IOException e) {
                logger.warn("Could not load previous orders, starting with empty state: {}", e.getMessage());
            }

            statePersister = new AsyncStatePersister(persistence);
            statePersister.start();

            OrderService orderService = new OrderService(restClient, stateManager, statePersister, exchangeInfoService,
                    config.getBaseAsset(), config.getQuoteAsset());

            userDataStreamService = new UserDataStreamService(restClient, stateManager, statePersister,
                    config.getWsBaseUrl(), config.getUserStreamKeepAliveMinutes());

            try {
                orderService.refreshOpenOrders();
            } catch (Exception e) {
                logger.warn("Could not reconcile open orders on startup: {}", e.getMessage());
            }

            int exitCode = new CommandLine(new OrderManagerCLI(balanceService, orderService, exchangeInfoService,
                    userDataStreamService, config.getBaseAsset(), config.getQuoteAsset()))
                    .execute(args);

            logger.debug("Command completed with exit code: {}", exitCode);
            if (userDataStreamService != null) {
                userDataStreamService.stop();
            }
            statePersister.shutdown(5);
            restClient.shutdown();

            System.exit(exitCode);

        } catch (ConfigurationException e) {
            logger.error("Configuration error", e);
            System.exit(1);

        } catch (Exception e) {
            logger.error("Fatal error", e);

            if (statePersister != null) {
                try {
                    statePersister.shutdown(5);
                } catch (Exception cleanupError) {
                    logger.error("Error during emergency cleanup", cleanupError);
                }
            }
            if (userDataStreamService != null) {
                try {
                    userDataStreamService.stop();
                } catch (Exception cleanupError) {
                    logger.error("Error during user stream cleanup", cleanupError);
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

    private static void configureLogLevel(String[] args) {
        boolean verbose = Arrays.stream(args).anyMatch(Main::isVerboseFlagPresent);
        if (!verbose) {
            return;
        }

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);
    }

    private static boolean isVerboseFlagPresent(String arg) {
        if (arg.equals("--verbose")) {
            return true;
        }

        if (arg.startsWith("--verbose=")) {
            String[] parts = arg.split("=", 2);
            return parts.length == 2 && Boolean.parseBoolean(parts[1]);
        }

        return false;
    }
}
