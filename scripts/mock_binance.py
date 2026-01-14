from flask import Flask, jsonify, request, make_response
import time

app = Flask(__name__)

attempt_registry = {}

def get_attempt(endpoint):
    attempt_registry[endpoint] = attempt_registry.get(endpoint, 0) + 1
    return attempt_registry[endpoint]

def reset_attempt(endpoint):
    attempt_registry[endpoint] = 0

# time sync 503 Network
@app.route('/api/v3/time', methods=['GET'])
def mock_time():
    attempt = get_attempt('time')
    if attempt < 4:
        print(f"[TIME] Attempt {attempt}: Simulating 503 Service Unavailable")
        return jsonify({"msg": "Server Busy"}), 503
    
    print(f"[TIME] Attempt {attempt}: Success")
    reset_attempt('time')
    return jsonify({"serverTime": int(time.time() * 1000)})

# exchange information required for order validation
@app.route('/api/v3/exchangeInfo', methods=['GET'])
def mock_exchange_info():
    symbol = request.args.get('symbol', 'BTCUSDT')
    print(f"[INFO] Providing filters for {symbol}")
    return jsonify({
        "symbols": [{
            "symbol": symbol,
            "status": "TRADING",
            "baseAsset": "BTC",
            "quoteAsset": "USDT",
            "filters": [
                {"filterType": "PRICE_FILTER", "minPrice": "0.01", "maxPrice": "100000.00", "tickSize": "0.01"},
                {"filterType": "LOT_SIZE", "minQty": "0.0001", "maxQty": "100.00", "stepSize": "0.0001"},
                {"filterType": "MIN_NOTIONAL", "minNotional": "10.00"},
                {"filterType": "PERCENT_PRICE_BY_SIDE", "bidMultiplierUp": "1.2", "bidMultiplierDown": "0.8", "askMultiplierUp": "1.2", "askMultiplierDown": "0.8", "avgPriceMins": 5}
            ]
        }]
    })

# ticker price, 429 Rate Limit
@app.route('/api/v3/ticker/price', methods=['GET'])
def mock_ticker():
    attempt = get_attempt('ticker')
    if attempt == 4:
        print(f"[TICKER] Attempt {attempt}: Simulating 429 Rate Limit")
        return jsonify({"code": -1003, "msg": "Too many requests"}), 429
    
    reset_attempt('ticker')
    return jsonify({"symbol": "BTCUSDT", "price": "60000.00"})

# account balances
@app.route('/api/v3/account', methods=['GET'])
def mock_account():
    print("[ACCOUNT] Fetching balances")
    return jsonify({
        "balances": [
            {"asset": "BTC", "free": "1.50000000", "locked": "0.00000000"},
            {"asset": "USDT", "free": "10000.00000000", "locked": "500.00000000"}
        ]
    })

# order placement, 418 IP ban and then success
@app.route('/api/v3/order', methods=['POST'])
def mock_place_order():
    attempt = get_attempt('place_order')
    if attempt <= 4:
        print(f"[ORDER] Attempt {attempt}: Simulating 418 IP Ban")
        return jsonify({"code": -1003, "msg": "IP Banned"}), 418
    
    print(f"[ORDER] Attempt {attempt}: Success")
    reset_attempt('place_order')
    return jsonify({
        "symbol": "BTCUSDT",
        "orderId": 1234567,
        "clientOrderId": "",
        "transactTime": int(time.time() * 1000),
        "price": "55000.00",
        "origQty": "0.1",
        "executedQty": "0.0",
        "status": "NEW"
    })

if __name__ == '__main__':
    app.run(port=8080)