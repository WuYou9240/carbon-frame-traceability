#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
并发性能压测脚本（替代 JMeter，零额外依赖）

压测对象：
  1. 扫码溯源接口 GET /api/trace/{serialNo}   —— 消费者高频访问路径（Redis 缓存命中）
  2. 车架详情接口 GET /api/frames/{id}         —— 管理端高频访问路径（JWT 鉴权 + MySQL 查询）

方法：线程池 + 固定请求总量，统计吞吐量、平均/中位/P95/P99 响应时间、错误率。
输出：控制台表格 + perf_result.json（供论文第 6 章性能测试章节引用）

用法：
  python perf_test.py                     # 默认压 18080
  python perf_test.py --base http://localhost:18080 --total 400
"""

import argparse
import json
import statistics
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

import requests

LEVELS = [10, 50, 100, 200]


def login(base, user="admin", pwd="Qts@2026"):
    r = requests.post(f"{base}/api/auth/login", json={"username": user, "password": pwd}, timeout=10)
    r.raise_for_status()
    body = r.json()
    if body.get("code") != 0:
        raise RuntimeError(f"登录失败: {body}")
    return body["data"]["token"]


def pick_target(base, token):
    """挑一台已上链的车架做压测样本"""
    headers = {"Authorization": f"Bearer {token}"}
    r = requests.get(f"{base}/api/frames", params={"current": 1, "size": 1}, headers=headers, timeout=10)
    rows = r.json()["data"]["records"]
    if not rows:
        raise RuntimeError("库中没有车架数据，请先准备演示数据")
    serial = rows[0]["serialNo"]
    frame_id = rows[0]["id"]
    return serial, frame_id


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = (len(sorted_vals) - 1) * p / 100.0
    lo, hi = int(k), min(int(k) + 1, len(sorted_vals) - 1)
    return sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (k - lo)


def run_level(name, url, headers, concurrency, total):
    """固定总请求量，按并发度切分给若干线程"""
    latencies = []
    errors = [0]
    lock = threading.Lock()
    per_thread = max(1, total // concurrency)
    barrier = threading.Barrier(concurrency)

    def worker():
        local = []
        err = 0
        s = requests.Session()
        s.headers.update(headers)
        barrier.wait()  # 同时起跑，避免渐次启动导致并发度虚高
        for _ in range(per_thread):
            t0 = time.perf_counter()
            try:
                r = s.get(url, timeout=15)
                cost = (time.perf_counter() - t0) * 1000
                if r.status_code == 200:
                    local.append(cost)
                else:
                    err += 1
            except Exception:
                err += 1
        s.close()
        with lock:
            latencies.extend(local)
            errors[0] += err

    t_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        for _ in range(concurrency):
            pool.submit(worker)
    elapsed = time.perf_counter() - t_start

    latencies.sort()
    done = len(latencies) + errors[0]
    return {
        "场景": name,
        "并发数": concurrency,
        "总请求": done,
        "成功": len(latencies),
        "失败": errors[0],
        "耗时(s)": round(elapsed, 2),
        "吞吐量(TPS)": round(done / elapsed, 1) if elapsed > 0 else 0,
        "平均(ms)": round(statistics.fmean(latencies), 1) if latencies else 0,
        "中位(ms)": round(percentile(latencies, 50), 1),
        "P95(ms)": round(percentile(latencies, 95), 1),
        "P99(ms)": round(percentile(latencies, 99), 1),
        "最大(ms)": round(max(latencies), 1) if latencies else 0,
        "错误率(%)": round(errors[0] * 100.0 / done, 2) if done else 0,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:18080")
    ap.add_argument("--total", type=int, default=400, help="每档并发的总请求量")
    args = ap.parse_args()
    base = args.base.rstrip("/")

    token = login(base)
    serial, frame_id = pick_target(base, token)
    auth = {"Authorization": f"Bearer {token}"}
    print(f"样本车架：{serial} (id={frame_id})")
    print(f"每档请求量：{args.total}\n")

    scenarios = [
        ("扫码溯源查询", f"{base}/api/trace/{serial}", {}),
        ("二维码防伪校验", f"{base}/api/qr/{serial}/verify", {}),
        ("车架详情查询", f"{base}/api/frames/{frame_id}", auth),
    ]

    results = []
    for name, url, headers in scenarios:
        print(f"=== {name} ===  {url}")
        for c in LEVELS:
            res = run_level(name, url, headers, c, args.total)
            results.append(res)
            print(f"  并发 {c:>3}  TPS {res['吞吐量(TPS)']:>7}  平均 {res['平均(ms)']:>6}ms  "
                  f"P95 {res['P95(ms)']:>6}ms  P99 {res['P99(ms)']:>6}ms  错误率 {res['错误率(%)']}%")
        print()

    with open("perf_result.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    # 输出 Markdown 表，可直接粘进论文
    print("| 场景 | 并发数 | 总请求 | 成功 | 失败 | 耗时(s) | TPS | 平均(ms) | 中位(ms) | P95(ms) | P99(ms) | 错误率 |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in results:
        print(f"| {r['场景']} | {r['并发数']} | {r['总请求']} | {r['成功']} | {r['失败']} | "
              f"{r['耗时(s)']} | {r['吞吐量(TPS)']} | {r['平均(ms)']} | {r['中位(ms)']} | "
              f"{r['P95(ms)']} | {r['P99(ms)']} | {r['错误率(%)']}% |")
    print("\n结果已写入 perf_result.json")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"压测失败：{e}", file=sys.stderr)
        sys.exit(1)
