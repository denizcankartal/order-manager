# Order Manager CLI

This is a CLI for managing Spot LIMIT orders on the Binance Spot Testnet, allows users to place, cancel, list, and track orders.

## Quick Start

### Prerequisites

*   Java 21
*   Maven 3.8+
*   Docker (for containerized execution)
*   Binance Testnet API Credentials, which can be obtained from [here](https://testnet.binance.vision/)

### Installation & Running

First, configure your environment by copying the example file and adding your API credentials:
```bash
cp .env.example .env
```

You can run the application using either Docker or by building the JAR with Maven.

#### Running with Docker
```bash
docker compose build

docker compose run --rm order-manager balances
docker compose run --rm order-manager list

# To reset local state:
docker compose down -v
```

State is persisted in the named volume `order-manager-state` at `/home/app/.order-manager` (the CLI writes `orders.json` there).

#### Running with Java + Maven
```bash
# Compile, run tests, and package the executable JAR
mvn clean package

java -jar target/order-manager-1.0.0.jar balances
java -jar target/order-manager-1.0.0.jar --help
```
State is persisted in the `~/.order-manager/` directory.

## Commands & Example Session

Note: The symbol such as `BTCUSDT` is configured via environment variables `BASE_ASSET` and `QUOTE_ASSET`

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
$ java -jar target/order-manager-1.0.0.jar add --side BUY --price 65000.00 --qty 0.001

{
  "orderId": 123456,
  "clientOrderId": "cli-1736507400000",
  "status": "NEW"
}
```

#### `list`: List all open orders
```bash
$ java -jar target/order-manager-1.0.0.jar list

ORDER_ID     CLIENT_ID            SIDE   SYMBOL       PRICE          ORIG_QTY       EXEC_QTY       STATUS     UPDATE_TIME
--------------------------------------------------------------------------------------------------------------------------
123456       cli-1736507400000    BUY    BTCUSDT      65000.00       0.001          0.00000000     NEW        2026-01-10 10:30:00
```

#### `show`: Get detailed information for a single order
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

#### `cancel`: Cancel an order
```bash
$ java -jar target/order-manager-1.0.0.jar cancel --id 123456
--
{
  "orderId": 123456,
  "clientOrderId": "cli-1736507400000",
  "status": "CANCELED"
}
```

#### `stream`: Subscribe to real-time updates
This command opens a persistent WebSocket connection to receive real-time updates on orders and account activity.
```bash
$ java -jar target/order-manager-1.0.0.jar stream
--
User data stream started. Press Ctrl+C to stop.
...executionReport events will be logged here...
```

## System Design and Architecture

### System Overview

- **CLI (`com.ordermanager.cli`)**: Parses commands, validates inputs, and formats console output.
- **Service (`com.ordermanager.service`)**: Contains the core business logic, orchestrating API calls, state management, and order validation.
- **Client (`com.ordermanager.client`)**: A custom HTTP client responsible for all communication with the Binance API, including request signing and error handling.
- **Persistence (`com.ordermanager.persistence`)**: Handles the serialization of order data to and from the local filesystem.

### State Management & Reconciliation
The app maintains a local snapshot of all tracked orders to provide instant feedback and reduce reliance on API calls.
- **In-Memory Storage:** Orders are held in a `ConcurrentHashMap` within the `StateManager` for fast, thread-safe lookups by either `clientOrderId` or `orderId`.
- **Asynchronous Persistence:** To avoid blocking CLI operations, state is written to `orders.json` on a dedicated background thread managed by `AsyncStatePersister`, ensuring a async experience while guaranteeing eventual persistence.
- **State Reconciliation:** On startup, the application fetches all open orders from the exchange via `GET /api/v3/openOrders`. It then reconciles this information with the state loaded from `orders.json`, updating statuses for any orders that were changed.

### Order Validation & Filters
To prevent invalid requests from ever reaching the exchange, order parameters are pre-validated against the symbol's trading rules fetched from `GET /api/v3/exchangeInfo`.
- **Filters Validated:** `PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL`, and `PERCENT_PRICE_BY_SIDE`.
- **Auto-Adjustment:** For `PExperienceRICE_FILTER` (tick size) and `LOT_SIZE` (step size), the application automatically rounds the user's input down to the nearest valid value and prints a warning.
- **Fail-Fast (Correctness):** For critical filters like `MIN_NOTIONAL` and `PERCENT_PRICE_BY_SIDE`, the application fails immediately with a clear error message, as these cannot be safely auto-adjusted.

### Real-time Updates (WebSocket)
The optional `stream` command implements the User Data Stream.
- **Connection:** The `UserDataStreamService` establishes a persistent WebSocket connection to Binance.
- **Authentication:** The stream is authenticated using a signed request.
- **State Updates:** Upon receiving an `executionReport` event, the service updates the corresponding order's status and quantities in the `StateManager` and queues the new state for persistence.
- **Resilience:** The service includes automatic reconnection logic with exponential backoff to handle temporary network disruptions.

### Reliability: Retries & Time Sync
- **Retry Mechanism:** A generic `RetryUtils` class wraps critical API calls. It catches retriable errors (HTTP `429`, `418`, `5xx`, and Binance-specific codes like `-1021`) and retries the operation with an exponential backoff delay (1s, 2s, 4s, ...).
- **Clock Drift Handling:** The Binance API requires request timestamps to be close to the server's time. The `TimeSync` service fetches the official server time on startup to calculate a local clock offset. This offset is used in all subsequent signed requests to prevent timestamp-related errors (`-1021`). If a timestamp error still occurs, a resync is triggered immediately.

### Key Design Decisions
- **Custom REST/WebSocket Clients:** A custom client was built using OkHttp instead of binance-java-connector to demonstrate a clear understanding of HTTP, authentication, and error handling, and also keep dependencies minimal.
- **`BigDecimal` for Financial Calculations:** All prices, quantities, and notional values use `BigDecimal` to avoid floating-point inaccuracies, ensuring financial correctness.
- **Asynchronous Persistence:** Decoupling file I/O from the main application thread to maintain a responsive experience.
- **Pragmatic Concurrency:** Using standard concurrency utilities (`ConcurrentHashMap`, `ExecutorService`, `BlockingQueue`) appropriate for this project's needs without premature optimization (e.g., lock-free algorithms), which would be unnecessary for this use case.

## Assumptions & Limitations
- **Single Trading Pair:** The application is configured via environment variables to trade a single symbol (e.g. `BTCUSDT`) for the duration of its runtime.
- **Network Stability:** A stable internet connection is assumed for reliable operation, especially for the real-time `stream` command.
- **Not for HFT:** The application is designed for human traders. It is not optimized ultra low latency automated trading.

 
