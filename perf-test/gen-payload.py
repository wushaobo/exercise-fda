#!/usr/bin/env python3
"""Pre-generate a shuffled JSON array of CheckTransaction requests with exact
90/9/1 mix (clear / suspicious / fraud). ghz consumes the array in round-robin,
so the macro traffic ratio is exact without dynamic weighting.

Usage: python3 gen-payload.py [N]  > payload.json   (default N=1000)
"""
import json
import random
import sys

random.seed(42)  # reproducible mix

N = int(sys.argv[1]) if len(sys.argv) > 1 else 1000


def req(tid, amount, payee):
    return {
        "transaction_id": tid,
        "upstream_trace_id": tid,
        "payer_account_id": "payer-perf",
        "payee_account_id": payee,
        "amount": amount,
        "currency": "USD",
    }


n_clear = int(N * 0.90)
n_susp = int(N * 0.09)
n_fraud = N - n_clear - n_susp  # remainder ensures 1% when N small

reqs = (
    [req(f"c{i}", 100.0, "payee-normal") for i in range(n_clear)]
    + [req(f"s{i}", 50000.0, "payee-normal") for i in range(n_susp)]
    + [req(f"f{i}", 5000.0, "account-blocked-1") for i in range(n_fraud)]
)
random.shuffle(reqs)
json.dump(reqs, sys.stdout)

assert n_clear + n_susp + n_fraud == N
print(f"\n# generated {N} requests: {n_clear} clear / {n_susp} suspicious / {n_fraud} fraud", file=sys.stderr)
