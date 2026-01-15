package com.ordermanager;

import com.ordermanager.cli.OrderManagerCLI;
import com.ordermanager.client.BinanceRestClient;
import com.ordermanager.config.AppConfig;
import com.ordermanager.config.DatabaseConfig;
import com.ordermanager.exception.ConfigurationException;
import com.ordermanager.repository.JdbcOrdersRepository;
import com.ordermanager.repository.OrdersRepository;
import com.ordermanager.service.BalanceService;
import com.ordermanager.service.OrderService;
import com.ordermanager.service.StateManager;
import com.ordermanager.service.TimeSync;
import com.ordermanager.service.UserDataStreamService;
import com.ordermanager.util.RetryUtils;

import ch.qos.logback.classic.Level;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.Arrays;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        BinanceRestClient restClient = null;
        UserDataStreamService userDataStreamService = null;

        try {
            configureLogLevel(args);

            AppConfig config = AppConfig.loadFromEnv();
            TimeSync timeSync = new TimeSync(new OkHttpClient(), config.getBaseUrl());

            RetryUtils.executeWithRetry(() -> {
                timeSync.sync();
                return null;
            }, "Initial Time Synchronization", logger);

            restClient = new BinanceRestClient(config, timeSync);

            BalanceService balanceService = new BalanceService(restClient);

            DatabaseConfig databaseConfig = new DatabaseConfig(config);
            OrdersRepository ordersRepository = new JdbcOrdersRepository(databaseConfig.getJdbi());

            StateManager stateManager = new StateManager(ordersRepository);

            OrderService orderService = new OrderService(restClient, stateManager, config.getBaseAsset(),
                    config.getQuoteAsset());

            userDataStreamService = new UserDataStreamService(stateManager, config.getWsBaseUrl(), config.getApiKey(),
                    config.getApiSecret(), config.getRecvWindow());

            int exitCode = new CommandLine(new OrderManagerCLI(balanceService, orderService,
                    userDataStreamService, config.getBaseAsset(), config.getQuoteAsset()))
                    .execute(args);

            logger.debug("Command completed with exit code: {}", exitCode);
            userDataStreamService.stop();

            restClient.shutdown();

            System.exit(exitCode);

        } catch (ConfigurationException e) {
            logger.error("Configuration error", e);
            System.exit(1);

        } catch (Exception e) {
            logger.error("Fatal error", e);

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
                    logger.error("Error during rest client cleanup", cleanupError);
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
