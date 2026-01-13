# Order Manager CLI - Comprehensive QA Test Plan

## 2. Core Functionality Tests

These tests cover the main features of the CLI as described in the requirements.

### 2.1. `balances` Command

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **BAL-01** | **Happy Path & Formatting** | `java -jar ... balances` | A correctly formatted table is displayed with aligned "ASSET", "FREE", and "LOCKED" columns. It shows rows for the `BASE_ASSET` (BTC) and `QUOTE_ASSET` (USDT). |
| **BAL-02** | **Rate Limit Handling** | Run `scripts/cli_loop_test.py` against `balances` in a rapid loop. | The application does not crash. It logs "Retriable error..." messages with exponential backoff delays and eventually either succeeds or fails with a "Rate limit exceeded" error. |

### 2.2. `add` Command

*(Note: Use prices and quantities that are reasonable for the current BTCUSDT testnet market price.)*

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **ADD-01** | **Happy Path: Place a valid BUY order** | `java -jar ... add --side BUY --price 20000 --qty 0.001` | Success message is printed. The output includes a JSON object with a numeric `orderId`, a string `clientOrderId` (prefixed with `cli-`), and status "NEW". |
| **ADD-02** | **Happy Path: Place a valid SELL order with custom client ID** | `java -jar ... add --side SELL --price 90000 --qty 0.001 --client-id my-sell-1` (Requires sufficient BTC balance). | Success message is printed. JSON output shows the correct `orderId`, the custom `clientOrderId` "my-sell-1", and status "NEW". |
| **ADD-03** | **Idempotency: Re-submit with the same custom client ID** | 1. Execute ADD-02. <br> 2. Execute it again immediately. | The second command fails with a "Duplicate order sent" error message. The CLI does not crash. |
| **ADD-04** | **Balance Fail: Insufficient BUY balance** | `java -jar ... add --side BUY --price 20000 --qty 500` (Use a quantity far exceeding your USDT balance). | The command fails with a clear error message: "Insufficient USDT balance...". |
| **ADD-05** | **Balance Fail: Insufficient SELL balance** | `java -jar ... add --side SELL --price 90000 --qty 500` (Use a quantity far exceeding your BTC balance). | The command fails with a clear error message: "Insufficient BTC balance...". |
| **ADD-06** | **Filter: Auto-adjusts PRICE_FILTER (tickSize)** | `java -jar ... add --side BUY --price 20000.12345 --qty 0.001` (Assuming tick size is 0.01). | A "Warnings:" section appears in the output, stating "Price adjusted: 20000.12345 → 20000.12...". The order is placed successfully with the adjusted price. |
| **ADD-07** | **Filter: Auto-adjusts LOT_SIZE (stepSize)** | `java -jar ... add --side BUY --price 20000 --qty 0.00123456` (Assuming step size is 0.00001). | A "Warnings:" section appears, stating "Quantity adjusted: 0.00123456 → 0.00123...". The order is placed successfully with the adjusted quantity. |
| **ADD-08** | **Filter Fail: MIN_NOTIONAL** | `java -jar ... add --side BUY --price 1 --qty 1` (Assuming min notional is > $1). | The command fails with a "Filter violation" error related to MIN_NOTIONAL. The error message includes current filter values and suggests how to fix the issue. |
| **ADD-09** | **Filter Fail: PRICE_FILTER min/max** | `java -jar ... add --side BUY --price 0.00001 --qty 0.1` (Use a price below the symbol's `minPrice`). | The command fails with an error message explicitly mentioning the price is below the minimum. |
| **ADD-10**| **Filter Fail: LOT_SIZE min/max** | `java -jar ... add --side BUY --price 20000 --qty 0.00000001` (Use a quantity below the symbol's `minQty`). | The command fails with an error message explicitly mentioning the quantity is below the minimum. |
| **ADD-11**| **Filter Fail: PERCENT_PRICE_BY_SIDE** | `java -jar ... add --side BUY --price 100 --qty 0.1` (Use a price extremely far from the current market price). | The command fails with an "Order validation failed: Price ... out of allowed range..." error. |

### 2.3. `list` Command

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **LIST-01** | **Happy Path: List one open order** | 1. Execute ADD-01. <br> 2. `java -jar ... list` | A table is displayed containing exactly one order, matching the `orderId` and `clientOrderId` from ADD-01. The `updateTime` is in `yyyy-MM-dd HH:mm:ss` format. |
| **LIST-02** | **No open orders** | 1. Ensure no orders are open. <br> 2. `java -jar ... list` | The message "No open orders..." is printed. |
| **LIST-03** | **List does not show closed orders** | 1. Place an order (e.g., ADD-01). <br> 2. Cancel it (see CAN-01). <br> 3. `java -jar ... list` | The message "No open orders..." is printed. |
| **LIST-04** | **Stale Data Verification** | 1. Place a new order using the Binance website (not the CLI). <br> 2. Run `java -jar ... list`. | The new order does **not** appear, confirming that `list` uses local state. (Startup reconciliation is tested in STATE-02/03). |

### 2.4. `show` Command

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **SHOW-01**| **Happy Path: Show by orderId** | 1. Place an order (e.g., ADD-01) and note the `orderId`. <br> 2. `java -jar ... show --id <orderId>` | A full JSON object for the order is printed, containing all key fields (orderId, clientOrderId, symbol, side, price, status, etc.). |
| **SHOW-02**| **Happy Path: Show by clientOrderId** | 1. Place an order with a custom client ID (e.g., ADD-02). <br> 2. `java -jar ... show --id my-sell-1` | A full JSON object for the order is printed. |
| **SHOW-03**| **Show a closed (CANCELED) order** | 1. Place and then cancel an order. <br> 2. `java -jar ... show --id <orderId>` | The JSON output is displayed correctly with `status: "CANCELED"`. `show` fetches the latest state. |
| **SHOW-04**| **Show a non-existent order** | `java -jar ... show --id 99999999999` | The command fails with a clear "Order not found" error message. |

### 2.5. `cancel` Command

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **CAN-01** | **Happy Path: Cancel by orderId** | 1. Place an order (e.g., ADD-01) and note the `orderId`. <br> 2. `java -jar ... cancel --id <orderId>` | A success message is printed with a JSON object showing the `orderId` and a `status` of "CANCELED". |
| **CAN-02** | **Happy Path: Cancel by clientOrderId** | 1. Place an order with a custom client ID (e.g., ADD-02). <br> 2. `java -jar ... cancel --id my-sell-1` | A success message is printed with JSON showing the `clientOrderId` and a `status` of "CANCELED". |
| **CAN-03** | **Idempotency: Cancel an already canceled order** | 1. Execute CAN-01. <br> 2. Execute CAN-01 again with the same `orderId`. | The command completes successfully, reporting the order's terminal state (`status: "CANCELED"`). It does not fail. |
| **CAN-04** | **Idempotency: Cancel a filled order** | 1. Place an order that will fill immediately on Testnet. <br> 2. `java -jar ... cancel --id <orderId>` | The command completes successfully, reporting the order's terminal state (`status: "FILLED"`). |
| **CAN-05** | **Cancel a non-existent order** | `java -jar ... cancel --id 99999999999` | The command fails with a clear "Order not found" error message. |

---

## 3. Real-time & State Tests

### 3.1. State Persistence & Reconciliation

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **STATE-00** | **State file creation** | 1. Delete `~/.order-manager/orders.json`. <br> 2. Run `java -jar ... balances`. | The file `~/.order-manager/orders.json` is created with empty state. |
| **STATE-01** | **State is loaded on startup** | 1. Place an order. <br> 2. Stop the application. <br> 3. Restart and run `java -jar ... list`. | The `list` command shows the order from step 1. The `orders.json` file should contain the order details. |
| **STATE-02** | **Reconciliation on startup (detects closed order)** | 1. Place an order. <br> 2. Stop the application. <br> 3. Manually cancel the order via the Binance Testnet website. <br> 4. Restart and run `java -jar ... list`. | The app reconciles on startup. The `list` command should show "No open orders...", because the app detected the order was canceled on the exchange. |
| **STATE-03** | **Reconciliation on startup (finds new order)** | 1. Place an order on the Testnet website directly (not via the CLI). <br> 2. Run `java -jar ... list` (with a clean state). | The order placed directly on the website appears in the list. The app finds orders it didn't create itself. |
| **STATE-04** | **Pruning of terminal orders** | 1. Place and cancel an order. <br> 2. Check that the `orders.json` file shows the order with status `CANCELED`. <br> 3. Restart the app (`java -jar ... list`). | After reconciliation, the terminal order is pruned from the in-memory state (it won't appear in `list`). The `orders.json` file may still contain it until the next write. |

### 3.2. Bonus: Real-time Updates (`stream` command)

These tests require two separate terminal windows.

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **STR-01** | **Stream connection and subscription** | **Terminal 1:** `java -jar ... stream` | The message "User data stream started..." appears. Log output shows a successful WebSocket connection and subscription (`subscriptionId=...`). |
| **STR-02** | **Real-time NEW order update** | 1. Execute STR-01 in T1. <br> 2. **Terminal 2:** `java -jar ... add ...` | **T1:** A new `executionReport` message is logged for the new order, showing the `clientOrderId` and status "NEW". `orders.json` is updated. |
| **STR-03** | **Real-time CANCELED order update** | 1. With stream running, place an order from T2. <br> 2. From T2, cancel the same order. | **T1:** An `executionReport` is logged for the cancellation, showing status "CANCELED". `orders.json` is updated. |
| **STR-04** | **Real-time FILLED order update (Manual)** | 1. With stream running, place a BUY order with a price slightly *above* the current market ask on the Testnet website. <br> 2. From another account on Testnet, place a SELL order to fill it. | **T1:** `executionReport` events are logged, showing status change to "FILLED". The `executedQty` should update. `orders.json` is updated. |
| **STR-05** | **Stream reconnects on disconnect** | 1. With stream running, temporarily disable your machine's network connection and then re-enable it. | The stream logs a "closed" or "failure" event, followed by "Scheduling... reconnect". After a backoff delay, it reconnects and re-subscribes automatically. |
| **STR-06**| **Clean Shutdown** | With the stream running, press `Ctrl+C`. | The app logs "Sent userDataStream.unsubscribe" and shuts down gracefully. |
| **STR-07**| **Invalid WS Endpoint** | Set `BINANCE_WS_BASE_URL` to a bad URL in `.env` and run `stream`. | The command fails with a 404 error and a clear message "WebSocket endpoint not found...". |
| **STR-08**| **Gap Analysis: Stop Tracking** | 1. Fill or cancel an order while the stream is running. <br> 2. Verify `orders.json` is updated. <br> 3. Run `java -jar ... list`. | Verify if the filled/canceled order is removed from the list view. (The `list` command only shows active orders, so this should pass). |

---

## 4. Technical & Non-Functional Tests
### 4.3. End-to-End Timing (Simple)

| Test ID | Description | Steps to Execute | Expected Result |
| :--- | :--- | :--- | :--- |
| **PERF-01** | **Balances latency** | `python3 scripts/cli_loop_test.py --iterations 20 -- balances` | Summary includes avg/p50/p95 timing stats with no crashes. |
| **PERF-02** | **Show latency** | `python3 scripts/cli_loop_test.py --iterations 20 -- show --id <orderId>` | Summary includes avg/p50/p95 timing stats with no crashes. |
| **PERF-03** | **Add latency** | `python3 scripts/cli_loop_test.py --iterations 20 -- add --side BUY --price 20000 --qty 0.001 --client-id cli-{ts}-{i}` | Summary includes avg/p50/p95 timing stats; order placement succeeds or rate-limits, without crashing. |
| **PERF-04** | **Cancel latency** | `python3 scripts/cli_loop_test.py --iterations 20 -- cancel --id <orderId>` | Summary includes avg/p50/p95 timing stats with no crashes. |
