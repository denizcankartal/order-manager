#!/usr/bin/env python3
import argparse
import math
import os
import subprocess
import sys
import time


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run any CLI command in a tight loop to watch for crashes, rate-limit "
            "behavior, and end-to-end timing (JVM + network + CLI)."
        )
    )
    parser.add_argument(
        "--jar",
        default="target/order-manager-1.0.0.jar",
        help="Path to the order-manager jar.",
    )
    parser.add_argument(
        "--iterations",
        type=int,
        default=50,
        help="Number of times to run the command.",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=0.2,
        help="Seconds to sleep between iterations.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=30.0,
        help="Seconds before timing out a single run.",
    )
    parser.add_argument(
        "--log-dir",
        default="",
        help="Optional directory to write per-iteration logs.",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print full command output for each iteration.",
    )
    parser.add_argument(
        "command",
        nargs=argparse.REMAINDER,
        help="Command args to pass to the CLI, e.g. -- balances or -- list",
    )
    return parser.parse_args()


def run_once(cmd: list[str], timeout: float) -> dict:
    start = time.time()
    result = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=timeout,
    )
    duration = time.time() - start
    output = result.stdout or ""
    return {
        "exit_code": result.returncode,
        "duration": duration,
        "output": output,
        "retriable": "Retriable error" in output,
        "rate_limit": "Rate limit exceeded" in output,
    }


def write_log(log_dir: str, iteration: int, output: str) -> None:
    if not log_dir:
        return
    path = f"{log_dir}/balances-{iteration:04d}.log"
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(output)


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    sorted_vals = sorted(values)
    idx = int(math.ceil(pct * len(sorted_vals))) - 1
    idx = max(0, min(idx, len(sorted_vals) - 1))
    return sorted_vals[idx]


def render_command(args: list[str], iteration: int) -> list[str]:
    timestamp_ms = int(time.time() * 1000)
    rendered = []
    for arg in args:
        arg = arg.replace("{i}", str(iteration))
        arg = arg.replace("{ts}", str(timestamp_ms))
        rendered.append(arg)
    return rendered


def main() -> int:
    args = parse_args()
    cli_args = args.command if args.command else ["balances"]
    if cli_args and cli_args[0] == "--":
        cli_args = cli_args[1:]
    if not cli_args:
        print("ERROR: No command provided. Example: -- balances")
        return 2

    base_cmd = ["java", "-jar", args.jar]

    if args.log_dir:
        os.makedirs(args.log_dir, exist_ok=True)

    crashes = 0
    rate_limits = 0
    successes = 0
    retriable_seen = 0
    durations = []

    for i in range(1, args.iterations + 1):
        cmd = base_cmd + render_command(cli_args, i)
        try:
            result = run_once(cmd, args.timeout)
        except subprocess.TimeoutExpired:
            print(f"[{i:03d}] TIMEOUT after {args.timeout:.1f}s")
            crashes += 1
            continue

        write_log(args.log_dir, i, result["output"])

        if args.verbose:
            print(result["output"])

        durations.append(result["duration"])
        if result["exit_code"] != 0:
            crashes += 1

        if result["retriable"]:
            retriable_seen += 1

        if result["rate_limit"]:
            rate_limits += 1
            status = "RATE_LIMIT"
        elif result["exit_code"] == 0:
            successes += 1
            status = "OK"
        else:
            status = "FAIL"

        print(
            f"[{i:03d}] {status} "
            f"exit={result['exit_code']} "
            f"elapsed={result['duration']:.2f}s "
            f"retriable={'Y' if result['retriable'] else 'N'}"
        )

        if args.sleep > 0:
            time.sleep(args.sleep)

    avg = sum(durations) / len(durations) if durations else 0.0
    p50 = percentile(durations, 0.50)
    p95 = percentile(durations, 0.95)
    fastest = min(durations) if durations else 0.0
    slowest = max(durations) if durations else 0.0

    print("\nSummary:")
    print(f"  command: {' '.join(cli_args)}")
    print(f"  iterations: {args.iterations}")
    print(f"  successes: {successes}")
    print(f"  rate_limits: {rate_limits}")
    print(f"  retriable_seen: {retriable_seen}")
    print(f"  crashes (non-zero or timeout): {crashes}")
    print("  timing (seconds):")
    print(f"    avg: {avg:.2f}")
    print(f"    p50: {p50:.2f}")
    print(f"    p95: {p95:.2f}")
    print(f"    min: {fastest:.2f}")
    print(f"    max: {slowest:.2f}")

    if crashes > 0:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
