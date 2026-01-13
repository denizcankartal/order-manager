package com.ordermanager.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ordermanager.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles persistence of order state to/from disk at
 * ~/.order-manager/orders.json
 */
public class StatePersistence {

    private static final Logger logger = LoggerFactory.getLogger(StatePersistence.class);

    private static final String STATE_DIR = ".order-manager";
    private static final String STATE_FILE = "orders.json";

    private final Path stateFilePath;
    private final ObjectMapper objectMapper;

    public StatePersistence() {
        String userHome = System.getProperty("user.home");
        this.stateFilePath = Paths.get(userHome, STATE_DIR, STATE_FILE);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(stateFilePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create state directory: " + stateFilePath.getParent(), e);
        }
    }

    /**
     * Save orders to disk.
     *
     * @param orders Map of orders (clientOrderId -> Order)
     * @throws IOException if save fails
     */
    public void save(Map<String, Order> orders) throws IOException {
        OrderState state = new OrderState(orders);

        logger.debug("Saving {} orders to {}", orders.size(), stateFilePath);

        File file = stateFilePath.toFile();
        objectMapper.writeValue(file, state);

        logger.info("Saved {} orders to disk", orders.size());
    }

    /**
     * Load orders from disk.
     *
     * @return Map of orders (clientOrderId -> Order)
     * @throws IOException if load fails
     */
    public Map<String, Order> load() throws IOException {
        File file = stateFilePath.toFile();

        if (!file.exists()) {
            logger.info("State file does not exist, starting with empty state");
            return new HashMap<>();
        }

        OrderState state = objectMapper.readValue(file, OrderState.class);

        logger.info("Loaded {} orders from disk", state.getOrders().size());

        return state.getOrders();
    }
}
