# Order Manager CLI

![Java](https://img.shields.io/badge/Java-21-blue.svg)
![Maven](https://img.shields.io/badge/Maven-3.8+-red.svg)
![Docker](https://img.shields.io/badge/Docker-Supported-blue.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

CLI for managing Spot `LIMIT` orders on the Binance Spot Testnet, allows users to place, cancel, list, and track orders.

## Features

- **Full Order Lifecycle Management**: `add`, `cancel`, `list`, and `show` commands.
- **Real-time State Tracking**: A persistent WebSocket connection (`stream` command) for live order and account updates.
- **Robust State Management**: Maintains a local state cache with asynchronous persistence and reconciles with the exchange on startup to ensure data consistency.
- **Intelligent Order Validation**: Pre-validates orders against exchange filters (`PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL`, etc.), with auto-adjustment for tick and step sizes.
- **Resilient API Communication**: Features automatic retries with exponential backoff for rate limits and network errors.
- **Clock Drift Protection**: Proactively syncs with Binance server time to prevent timestamp-related API errors.

## Getting Started

### Prerequisites

*   Java 21
*   Maven 3.8+
*   Docker (optional, for containerized execution)
*   Binance Testnet API credentials, which can be obtained from [here](https://testnet.binance.vision/).

### Configure API Credentials

Copy the environment file template and add your Binance Testnet API key and secret.

```bash
cp .env.example .env
```

### Running the Application

One can run the application using either Docker Compose or by building the executable JAR with Maven.

#### Option 1: Running with Docker Compose

```bash
# Build the Docker image
docker compose build

docker compose run --rm order-manager balances
docker compose run --rm order-manager list --symbol BTCUSDT

# To reset the local order state, bring the volume down
docker compose down -v
```

State is persisted in a named Docker volume `order-manager-state`, which maps to `/home/app/.order-manager` inside the container.

#### Option 2: Running with Java + Maven

Build the project from source and run the JAR directly.

```bash
# Compile, run tests, and package the executable JAR
mvn clean package

# Run any command
java -jar target/order-manager-1.0.0.jar balances
java -jar target/order-manager-1.0.0.jar --help
```

State is persisted in the `~/.order-manager/` directory in your home folder.

## Example session

Note: The primary trading symbol (e.g., `BTCUSDT`) is configured via the `BASE_ASSET` and `QUOTE_ASSET` environment variables.

#### `balances`: Check account balances

```bash
$ java -jar target/order-manager-1.0.0.jar balances

ASSET      FREE                 LOCKED
----------------------------------------------
BTC        1.50000000           0.00000000
USDT       10000.00000000       0.00000000
```

#### `add`: Place a new LIMIT order

```bash
$ java -jar target/order-manager-1.0.0.jar add --side BUY --price 90000.00 --qty 0.001

{
  "orderId": 123456,
  "clientOrderId": "cli-1736507400000",
  "status": "NEW"
}
```

#### `list`: List all open orders from local state

```bash
$ java -jar target/order-manager-1.0.0.jar list

ORDER_ID  CLIENT_ID          SIDE  SYMBOL   PRICE     ORIG_QTY  EXEC_QTY  STATUS  UPDATE_TIME
-----------------------------------------------------------------------------------------------------------------------
123456    cli-1736507400000  BUY   BTCUSDT  90000.00  0.001     0.000000  NEW     2026-01-10 10:30:00
```

#### `show`: Fetch detailed information for a single order from the exchange

```bash
$ java -jar target/order-manager-1.0.0.jar show --id 123456

{
  "orderId": 123456,
  "clientOrderId": "cli-1736507400000",
  "symbol": "BTCUSDT",
  "side": "BUY",
  "price": "65000.00",
  "origQty": "0.001",
  "executedQty": "0.000000",
  "status": "NEW",
  "updateTime": 1736507400000
}
```

#### `cancel`: Cancel an open order

```bash
$ java -jar target/order-manager-1.0.0.jar cancel --id 123456

{
  "orderId": 123456,
  "clientOrderId": "cli-1736507400000",
  "status": "CANCELED"
}
```

#### `stream`: Subscribe to real-time account and order updates

This command opens a persistent WebSocket to receive real-time `executionReport` events.

```bash
$ java -jar target/order-manager-1.0.0.jar stream

User data stream started. Press Ctrl+C to stop.
...executionReport events will be logged here in real-time...
```

## Design and Architecture

### System Overview

-   **CLI (`com.ordermanager.cli`)**: Parses commands, validates user inputs, and formats console output. It acts as the entry point and controller layer.
-   **Service (`com.ordermanager.service`)**: Contains the core business logic, orchestrating API calls, state management, and order validation.
-   **Client (`com.ordermanager.client`)**: A custom HTTP/WebSocket client layer responsible for all communication with the Binance API, including request signing and error handling.
-   **Persistence (`com.ordermanager.persistence`)**: Handles the serialization and deserialization of order data to and from the local filesystem (`orders.json`).

### State Management & Reconciliation

The application maintains a local snapshot of all tracked orders to provide instant feedback (`list` command) and reduce reliance on API calls.

-   **In-Memory Storage:** Orders are held in a `ConcurrentHashMap` within the `StateManager` for fast, thread-safe lookups by either `clientOrderId` or `orderId`.
-   **Asynchronous Persistence:** To avoid blocking CLI operations, state is written to disk on a dedicated background thread managed by `AsyncStatePersister`. This ensures a responsive user experience while guaranteeing eventual persistence.
-   **Startup Reconciliation:** On startup, the application fetches all open orders from the exchange (`GET /api/v3/openOrders`). It then reconciles this information with the state loaded from `orders.json`, updating statuses for any orders that were modified or closed while the CLI was offline.

### Order Validation & Filters

To prevent invalid requests, order parameters are pre-validated against the symbol's trading rules (`GET /api/v3/exchangeInfo`).

-   **Filters Validated:** `PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL`, and `PERCENT_PRICE_BY_SIDE`.
-   **Auto-Adjustment:** For `PRICE_FILTER` (tick size) and `LOT_SIZE` (step size), the application automatically rounds the user's input down to a valid value and notifies the user with a warning.
-   **Fail-Fast:** For critical filters like `MIN_NOTIONAL`, the application fails immediately with a clear error message, as these cannot be safely auto-adjusted.

### Real-time Updates

The `stream` command implements the User Data Stream for real-time updates.

-   **Connection & Authentication:** The `UserDataStreamService` establishes and maintains an authenticated, persistent WebSocket connection.
-   **Live State Updates:** Upon receiving an `executionReport` event, the service immediately updates the order's status and quantities in the `StateManager` and queues the new state for persistence.

### Reliability and Error Handling

-   **Retry Mechanism:** Critical API calls are wrapped in a generic `RetryUtils` class. It catches retriable network errors (HTTP `429`, `418`, `5xx`) and retries the operation with an exponential backoff delay.
-   **Clock Drift Handling:** The application fetches the Binance server time via `TimeSync` to calculate a local clock offset. 

### Key Architectural Decisions

-   **Custom API Client:** A custom client was built using OkHttp to demonstrate a understanding of HTTP communication, request signing, and error handling, while also minimizing external dependencies.
-   **BigDecimal for Financial Calculations:** All prices, quantities, and notional values use `BigDecimal` to avoid floating-point inaccuracies, ensuring correctness in all financial operations.
-   **Asynchronous Persistence:** Decoupling file I/O from the main application thread to maintain a responsive CLI experience.

## Limitations & Assumptions

-   **Single Trading Pair:** The application is designed to trade a single symbol (e.g., `BTCUSDT`) per session, configured via environment variables.
-   **Not for High-Frequency Trading (HFT):** The CLI is designed primarly for human traders. It is not fully optimized for ultra-low latency demands.
-   **Network Stability:** A stable internet connection is assumed for reliable operation, especially for the real-time `stream` command.