#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缓存旁路（Cache-Aside）冷热路径对照实验

目的：为论文第 6 章提供「同一接口、同一份数据、唯一差异是缓存是否命中」的对照数据。
方法：
  冷路径 —— 每次请求前用 RESP 协议 DEL 掉 trace:{serialNo}，强制走 MySQL 组装 + 哈希重算 + JSON 序列化；
  热路径 —— 先预热一次，随后连续请求，全部命中 Redis。
两条路径使用同一进程、同一数据、单线程顺序执行，排除并发干扰与网络抖动。

输出：cache_result.json
"""

import json
import os
import socket
import statistics
import time

import requests

BASE = os.getenv("BASE_URL", "http://127.0.0.1:18080")
REDIS = (os.getenv("REDIS_HOST", "127.0.0.1"), int(os.getenv("REDIS_PORT", "6379")))
ROUNDS = 60


def redis_cmd(*args):
    s = socket.create_connection(REDIS, timeout=5)
    payload = ("*%d\r\n" % len(args)).encode()
    for a in args:
        b = a.encode() if isinstance(a, str) else a
        payload += b"$%d\r\n" % len(b) + b + b"\r\n"
    s.sendall(payload)
    data = s.recv(4096)
    s.close()
    return data


def login():
    r = requests.post(BASE + "/api/auth/login",
                      json={"username": "admin", "password": "Qts@2026"}, timeout=10)
    return r.json()["data"]["token"]


def pick_serial(token):
    r = requests.get(BASE + "/api/frames", params={"current": 1, "size": 1},
                     headers={"Authorization": "Bearer " + token}, timeout=10)
    return r.json()["data"]["records"][0]["serialNo"]


def bench(url, rounds, evict_key=None):
    lat = []
    for _ in range(rounds):
        if evict_key:
            redis_cmd("DEL", evict_key)
        t0 = time.perf_counter()
        r = requests.get(url, timeout=15)
        dt = (time.perf_counter() - t0) * 1000
        if r.status_code != 200:
            raise RuntimeError("状态码 %s" % r.status_code)
        lat.append(dt)
    lat.sort()
    return {
        "轮次": rounds,
        "平均(ms)": round(statistics.mean(lat), 2),
        "中位(ms)": round(statistics.median(lat), 2),
        "P95(ms)": round(lat[int(len(lat) * 0.95) - 1], 2),
        "最小(ms)": round(lat[0], 2),
        "最大(ms)": round(lat[-1], 2),
    }


def main():
    token = login()
    serial = pick_serial(token)
    url = "%s/api/trace/%s" % (BASE, serial)
    key = "trace:" + serial
    print("样本车架:", serial)

    # 预热，确保热路径首轮即命中
    requests.get(url, timeout=15)
    time.sleep(0.3)

    print("开始冷路径（每次先删缓存）...")
    cold = bench(url, ROUNDS, evict_key=key)
    print("开始热路径（缓存命中）...")
    hot = bench(url, ROUNDS)

    cold["路径"] = "缓存未命中"
    hot["路径"] = "缓存命中"
    speedup = cold["平均(ms)"] / hot["平均(ms)"]
    result = {
        "样本车架": serial,
        "接口": "GET /api/trace/{serialNo}",
        "明细": [cold, hot],
        "加速比": round(speedup, 2),
    }
    with open("cache_result.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
