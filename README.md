# Order Manager CLI

CLI for managing Spot `LIMIT` orders on the Binance Spot Testnet, allows users to place, cancel, list, and track orders.

- A WebSocket connection for live order updates when placing an order.
- Pre-validates orders against exchange filters (`PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL`, `PERCENT_PRICE_BY_SIDE`), with auto-adjustment for tick and step sizes.
- Implements automatic retries with exponential backoff for rate limits and network errors.
- Syncs with Binance server time to prevent timestamp-related API errors.
- Persisted internal state via Postgres using JdbcOrdersRepository

## Quick Start

### Prerequisites

*   Java 21
*   Maven 3.8+
*   Docker (for containerized execution)
*   Binance Testnet API credentials, which can be obtained from [here](https://testnet.binance.vision/).
*   Python3, Flask (for mocking binance server)

### Environment Variables

```bash
cp .env.example .env
```

```bash
BINANCE_API_KEY=testnet_api_key
BINANCE_API_SECRET=testnet_api_secret
BINANCE_BASE_URL=https://testnet.binance.vision
# BINANCE_BASE_URL=http://localhost:8080 # uncomment for mock server
BINANCE_WS_BASE_URL=wss://ws-api.testnet.binance.vision/ws-api/v3
BINANCE_RECV_WINDOW=10000
USER_STREAM_KEEPALIVE_MINUTES=30
BASE_ASSET=BTC
QUOTE_ASSET=USDT
DB_URL=jdbc:postgresql://db:5432/order_manager
DB_USER=order_user
DB_PASSWORD=order_pass
```

### Database Setup

```bash
# (one-time) create shared network
docker network create order-manager-net

# start postgres in the background
docker compose -f docker-compose.db.yml up -d

# wait until databse is healthy
docker compose -f docker-compose.db.yml ps

# apply schema first time
docker exec -i order-manager-db psql -U order_user -d order_manager < src/main/resources/db/migration/v1_create_orders.sql

# wipe the data if you need to
docker compose -f docker-compose.db.yml down -v
```
### Running the Application

```bash
# Build the Docker image
docker compose build

docker compose run --rm order-manager balances
docker compose run --rm order-manager list --symbol BTCUSDT

# To reset the local order state, bring the volume down
docker compose down -v

# To stop/reset the database
docker compose -f docker-compose.db.yml down -v
```

## Example session

Note: The primary trading symbol (e.g., `BTCUSDT`) is configured via the `BASE_ASSET` and `QUOTE_ASSET` environment variables.

`balances`

- Prints free/locked balances for BASE_ASSET and QUOTE_ASSET.

```bash
$ docker compose run --rm order-manager balances

ASSET      FREE                 LOCKED
----------------------------------------------
BTC        1.50000000           0.00000000
USDT       10000.00000000       0.00000000
```

`add`

- Places a LIMIT order for BASE_ASSET+QUOTE_ASSET.
- Validates against PRICE_FILTER, LOT_SIZE, MIN_NOTIONAL, and PERCENT_PRICE_BY_SIDE.
- Auto‑adjusts price/qty down to tick/step size when applicable and prints warnings.
- If the order is not terminal, it starts WebSocket tracking and persists execution updates until the order is FILLED or CANCELED.

```bash
$ docker compose run --rm order-manager add --side BUY --price 90000 --qty 0.0001 --client-id my-order
Order placed: id=1309715 clientId=my-order side=BUY 0.0001 BTCUSDT @ 90000 status=NEW
{
  "orderId": 1309715,
  "clientOrderId": "my-order",
  "status": "NEW",
}
User data stream started. Press Ctrl+C to stop.
```

`list`
- List all open orders from local

```bash
$ docker compose run --rm order-manager list
ORDER_ID     CLIENT_ID          SIDE   SYMBOL       PRICE          ORIG_QTY       EXEC_QTY       STATUS     UPDATE_TIME       
----------------------------------------------------------------------------------------------------------
1309715      my-order           BUY    BTCUSDT      90000          0.0001         0.00000000     NEW        2026-01-15 01:22:46
```

`show` 
- Prints a JSON view of a single order from local

```bash
$ docker compose run --rm order-manager show --id my-order
{
  "orderId": 1309715,
  "clientOrderId": "my-order",
  "symbol": "BTCUSDT",
  "side": "BUY",
  "price": "90000",
  "origQty": "0.0001",
  "executedQty": "0E-8",
  "status": "NEW",
  "updateTime": 1768436566948
}
```

`cancel`
- Cancels an order by exchange orderId or client order ID.
- Idempotent: if already terminal, returns the existing state.

```bash
$ docker compose run --rm order-manager cancel --id my-order
{
  "orderId": 1309715,
  "clientOrderId": "my-order",
  "status": "CANCELED",
}
```

```bash
$ docker compose run --rm order-manager cancel --id my-order
Order already is CANCELLED: my-order
{
  "orderId": 1309715,
  "clientOrderId": "my-order",
  "status": "CANCELED",
}
```

Global flag:

- --verbose enables sanitized HTTP request/response logging redacting API key and signatures.

## Testing

### Automated Tests
```bash
mvn test
```

### Local Mock Binance Server
```bash
cd scripts

python3 -m venv venv

source venv/bin/activate

pip install flask

python mock_binance.py
```

- Change BINANCE_BASE_URL in `.env` to http://localhost:8080
- run order-manager command to test how order manager handles rate limits and network errors by retrying and exponentially backing off.

```bash
$ docker compose run --rm order-manager add --side BUY --price 70000 --qty 0.001

2026-01-15 01:39:43.966 [main] WARN  com.ordermanager.Main - Retriable error on attempt 1/5 for Initial Time Synchronization: Server time sync failed. Retrying in 1000ms...
2026-01-15 01:39:44.977 [main] WARN  com.ordermanager.Main - Retriable error on attempt 2/5 for Initial Time Synchronization: Server time sync failed. Retrying in 2000ms...
2026-01-15 01:39:46.983 [main] WARN  com.ordermanager.Main - Retriable error on attempt 3/5 for Initial Time Synchronization: Server time sync failed. Retrying in 4000ms...
2026-01-15 01:39:51.273 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=Too many requests
2026-01-15 01:39:51.273 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 1/5 for fetch ticker price: Too many requests. Retrying in 1000ms...
2026-01-15 01:39:52.279 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=Too many requests
2026-01-15 01:39:52.280 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 2/5 for fetch ticker price: Too many requests. Retrying in 2000ms...
2026-01-15 01:39:54.284 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=Too many requests
2026-01-15 01:39:54.284 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 3/5 for fetch ticker price: Too many requests. Retrying in 4000ms...
2026-01-15 01:39:58.379 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=IP Banned
2026-01-15 01:39:58.379 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 1/5 for place order: IP Banned. Retrying in 1000ms...
2026-01-15 01:39:59.388 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=IP Banned
2026-01-15 01:39:59.389 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 2/5 for place order: IP Banned. Retrying in 2000ms...
2026-01-15 01:40:01.394 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=IP Banned
2026-01-15 01:40:01.394 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 3/5 for place order: IP Banned. Retrying in 4000ms...
2026-01-15 01:40:05.406 [main] ERROR c.o.client.BinanceRestClient - Binance API error: code=-1003, msg=IP Banned
2026-01-15 01:40:05.408 [main] WARN  c.ordermanager.service.OrderService - Retriable error on attempt 4/5 for place order: IP Banned. Retrying in 8000ms...
Order placed: id=1234567 clientId=cli-1768437591259-b9363185 side=BUY 0.001 BTCUSDT @ 70000 status=NEW
{
  "orderId": 1234567,
  "clientOrderId": "cli-1768437591259-b9363185",
  "status": "NEW",
}
User data stream started. Press Ctrl+C to stop.
```

## Decisions

-   **Custom API Client:** A custom client was built using OkHttp to demonstrate an understanding of HTTP communication, request signing, and error handling, while also minimizing external dependencies.
-   **BigDecimal for Financial Calculations:** All prices, quantities, and notional values use `BigDecimal` to avoid floating-point inaccuracies, ensuring correctness in all financial operations.

## Limitations & Assumptions

-   **Not for High-Frequency Trading (HFT):** The CLI is designed primarly for human traders. It is not fully optimized for ultra-low latency demands.
-   **Network Stability:** A stable internet connection is assumed.
