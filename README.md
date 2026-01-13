This is a CLI for managing Spot LIMIT orders on Binance Spot Testnet, enabling users to place, cancel, list, and track orders with real-time updates via WebSocket.

## Quick Start

### Prerequisites

- Java 21
- Maven 3.8
- Docker
- Get Binance Testnet API Credentials from [here](https://testnet.binance.vision/)

### Build and Run

```bash
# Compile and test
mvn clean test

# Package the project as JAR
mvn clean package

# Configure environment variables 
cp .env.example .env

# Run
java -jar target/order-manager-1.0.0.jar --help
```

### Docker

```bash
# Build image
docker compose build

# Run commands
docker compose run --rm order-manager balances
docker compose run --rm order-manager list
docker compose run --rm order-manager add --side BUY --price 10000 --qty 0.001
docker compose run --rm order-manager cancel --id 123456
docker compose run --rm order-manager show --id 123456
docker compose run --rm order-manager --verbose balances

# To reset local state:
docker compose down -v
```

State is persisted in the named volume `order-manager-state` at `/home/app/.order-manager` (the CLI writes `orders.json` there).

### Commands

```bash
# Print free/locked balances for relevant assets of the traded
order_manager balances

# Place a LIMIT order
order_manager add --side BUY|SELL --symbol BTCUSDT --price 10.15 --qty 10 [--client-id myid-123]

# Cancel an order by orderId or origClientOrderId.
order_manager cancel --id <orderId|origClientOrderId>

# List open orders
order_manager list [--symbol BTCUSDT]

# Show order details given orderId or origClientOrderId
order_manager show --id <orderId|origClientOrderId>

# Verbose HTTP logging (redacted signatures)
order_manager --verbose add --side BUY --symbol BTCUSDT --price 10000 --qty 0.001
```

### Example Session

```bash
# Check balances
order_manager balances
--
Asset | Free        | Locked
------|-------------|--------
BTC   | 1.50000000  | 0.00000000
USDT  | 10000.00000 | 0.00000
--

# Place a buy order
order_manager add --side BUY --symbol BTCUSDT --price 90000.00 --qty 0.001
--
Order placed successfully
{"orderId": 123456, "clientOrderId": "cli-1234567890", "status": "NEW"}
--

# List open orders
order_manager list --symbol BTCUSDT
--
OrderId | Side | Price     | OrigQty  | ExecutedQty | Status | UpdateTime
--------|------|-----------|----------|-------------|--------|-------------------
123456  | BUY  | 90000.00  | 0.001000 | 0.000000    | NEW    | 2026-01-10 10:30:00
--

# Show order details
order_manager show --id 123456
--
{
  "orderId": 123456,
  "clientOrderId": "cli-1234567890",
  "symbol": "BTCUSDT",
  "side": "BUY",
  "type": "LIMIT",
  "price": "90000.00",
  "origQty": "0.001000",
  "executedQty": "0.000000",
  "status": "NEW",
  "timeInForce": "GTC",
  "updateTime": 1736507400000
}
--

# Cancel the order
order_manager cancel --id 123456
--
Order canceled successfully
{"orderId": 123456, "status": "CANCELED"}
--

## Design Notes
- State: in-memory `StateManager` keyed by `clientOrderId`, persisted asynchronously to `~/.order-manager/orders.json`.
- Reconciliation: each list/show/refresh call hits Binance REST first, then reconciles local state (missing active orders are refetched).
- Validation: PRICE_FILTER, LOT_SIZE auto-round down with warnings; MIN_NOTIONAL and PERCENT_PRICE_BY_SIDE fail fast with clear messages.
- Reliability: signed requests use HMAC-SHA256; retries with backoff 1/2/4/8/16s on 418/429/5xx/-1021; a timestamp error triggers an immediate time resync.
- Logging: `--verbose` raises logging to DEBUG and redacts signatures in URLs.
```

## Architecture
### System Design

TODO: THIS WILL COME LATER
### Key Components

**Order Lifecycle**
```bash
PENDING NEW (Local state) --- POST /api/v3/order --> NEW (Order accepted by exchange) --- Market Fills --> PARTIALLY FILLED / FILLED
                                                                                       |
                                                                                       -- Error ---------> REJECTED
                                                                                       |
                                                                                       -- User Cancels --> CANCELLED

```
**Internal State: In-Memory + Disk Persistence**

```bash
{
  clientOrderId,
  orderId,
  symbol,
  side,
  price,
  origQty,
  executedQty,
  status,
  updateTime
}
```

Sources of truth:
1. Binance exchange
2. Local in-memory (fast, bare minimum latency)
3. Disk state (persistent, may be slightly stale, eventual consistency)

How is state manipulated:
1. On startup: Load state from disk -> query exchange to compare
2. On place order: REST response -> update local state -> async disk write
3. On websocket event: Update local state -> async disk write
4. On periodic query command: Update local state -> async disk write

How is state implemented:
- In-memory, orders are stored in a `ConcurrentHashMap` for fast O(1) access and thread-safe concurrent updates.
- For persistence, orders are written asynchronously to a json file `orders.json` on disk to survive application restarts.
- Exchange is queried periodically to catch missed events

**Filter Validation & Auto-Adjustment**

Before placing an order, the application validates price and quantity against symbol-specific filters from `/api/v3/exchangeInfo`:

**Filters Checked**:
- **LOT_SIZE**: Validates quantity is within min/max and aligns with step size
- **PRICE_FILTER**: Validates price is within min/max and aligns with tick size
- **MIN_NOTIONAL**: Ensures price × quantity meets minimum order value
- **PERCENT_PRICE_BY_SIDE**: Ensures price is within acceptable range of current market price

**Validation Strategy**:
1. Fetch filters on first use, cache them in memory (filters rarely change)
2. Validate user input against all applicable filters
3. If invalid, auto-adjust (round down to nearest valid value) and warn user
4. If adjustment impossible (e.g., below minimum), reject with clear error message

**Example**:
```
User input: --price 45123.456 --qty 0.0012345
Adjusted:   --price 45123.00  --qty 0.00123000
Warning: Price rounded to tick size 1.00, Quantity rounded to step size 0.00001000
```

**Retry Strategy**

Transient errors (network issues, rate limits) are retried with exponential backoff:

```
Attempt 1: delay = 1s
Attempt 2: delay = 2s
Attempt 3: delay = 4s
Attempt 4: delay = 8s
Attempt 5: delay = 16s (max attempts reached, fail)
```

**Retriable errors**:
- HTTP 429 (Rate limit exceeded)
- HTTP 418 (IP banned temporarily)
- HTTP 5xx (Network errors)

**Non-retriable errors**:
- HTTP 4xx (Business Errors e.g. bad request, unauthorized, forbidden)

### Thread Model

Several concurrent threads are used for optimal performance and responsiveness:

**Thread 1: Main Thread (CLI)**
Purpose: User interaction and command execution

Responsibilities:

- Parse CLI arguments
- Execute service calls (place, cancel, list, show, balances)
- Query state (in-memory, non-blocking)
- Display formatted output

Blocking: Only on network I/O (REST API calls)

**Thread 2: WebSocket Receiver (Producer)**

Purpose: Receive real-time events from Binance

Responsibilities:

- Maintain WebSocket connection
- Parse incoming JSON messages -> ExecutionReportEvent objects
- Push events to BlockingQueue (non-blocking offer)
- Handle reconnection with exponential backoff

Blocking: Never (uses non-blocking queue offer)

**Thread 3: Event Processor (Consumer)**

Purpose: Process WebSocket events asynchronously

Responsibilities:

- Poll BlockingQueue
- Update StateManager with order status changes
- Log significant events (fills, cancellations) (TODO: SHOULD WE HAVE A SEPERATE THREAD FOR LOGGIN)
- Trigger async disk writes

Blocking: Only when queue is empty (waiting for events)

**Thread 4: Disk Writer**

Purpose: Asynchronous state persistence

Responsibilities:

- Poll write queue for state snapshots
- Write JSON to disk
- Handle I/O errors gracefully

Blocking: During disk I/O

**Thread 5: Keepalive Scheduler**

Purpose: Extend WebSocket listenKey lifetime

Responsibilities:

- Send PUT request to Binance every 30 minutes
- Prevent listenKey expiration (60-minute lifetime)

Blocking: Only during HTTP request

## Design Decisions

**Custom REST/WebSocket Clients**

Decision: Implement custom REST and WebSocket clients instead of using binance java connector.

- Demonstrate HTTP/WebSocket knowledge
- Control retry logic and error handling
- Keep dependencies minimal and transparent

**Producer-Consumer event pipeline for websocket events**

Decision: Decouple web socket event handling from processing using BlockingQueue.

- Non-blocking WebSocket thread never waits for state updates on disk
- Can add multiple consumer threads if scaling is needed
- Separation of Concerns by decoupling network I/O from business logic

**In-memory + async disk persistence**

Decision: Use ConcurrentHashMap for primary state, with asynchronous JSON file writes.

- Fast Queries, in-memory lookup is O(1)
- Crash Resilience, state persists to disk, survives restarts
- Non-blocking async writes don't delay user commands

**Auto-Adjust filters with warnings**

Decision: Automatically round prices/quantities to valid values, warn user.

- Better UX because adjusting is friendlier than rejecting
- Prevents common mistake (too many decimals)

**BigDecimal for financial calculations**

Decision: Use BigDecimal for all prices, quantities, and amounts.

- Correctness: No floating-point rounding errors. (Although slower than double operation still negligible for this cli)

**Exponential Backoff for Retries**

Decision: Retry failed requests with exponential backoff.  1 -> 2 -> 4 -> 8 -> 16 -> give up

- Prevents overwhelming and doesnt hammer exchange during outages
- Graceful Degradation, gives exchange time to recover

**No Optimization**

Decision: No low-level optimization since the CLI is assumed to be operated by a human trader and because the network latency between the CLI and binance servers dwarfs any code optimization. For a co-located HFT system processing 10,000 events/second, I would use different techniques: reduce GC pauses lock-free queues, zero-copy buffers, thread pinning. Most of our latency comes from network and unless we colocate, optimizing code from x ms to x/10 or even x/100 ms will not save us in 10x (most of this 10x is network latency) total operation. 

TODO: PROVIDE SOME ACTUAL NUMBERS AND METRICS IF TIME ALLOWS. 

For now no need to implement:
- Lock-free data structures everywhere (ConcurrentHashMap is fine currently)
- Object pooling (GC overhead is negligible)
- Zero-copy deserialization
- Custom memory allocators

## Assumptions & Limitations
- Stable internet connection for WebSocket
- System clock is reasonably accurate (within 1 minute of UTC)
- Binance Testnet is available and responsive
- CLI is expected to be used by human traders, not part of a HFT system therefore, CLI is not specifically designed for high-frequency trading.
 
