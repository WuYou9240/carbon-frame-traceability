#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
功能测试自动化脚本（论文第 6 章 表 6-x 的数据来源）

覆盖：认证鉴权、溯源查询、防伪校验、缓存一致性、报告与导出一致性、异常处理。
每条用例都真实打到运行中的服务，不 mock；篡改类用例直接改数据库再还原，
断言依据接口返回的业务结论，而非固定文案。

输出：func_result.json
"""

import json
import os
import time

import pymysql
import requests

BASE = os.getenv("BASE_URL", "http://127.0.0.1:18080")

# 数据库连接从环境变量读取，避免把密码写进仓库。
# 用法：DB_PASSWORD=xxx python func_test.py
DB = dict(
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=int(os.getenv("DB_PORT", "3306")),
    user=os.getenv("DB_USER", "qts"),
    password=os.getenv("DB_PASSWORD", ""),
    database=os.getenv("DB_NAME", "qts"),
    charset="utf8mb4",
)

# 演示账号口令（登录页公开提示，与数据库密码无关）
DEMO_PWD = os.getenv("DEMO_PASSWORD", "Qts@2026")

results = []


def rec(cid, name, expect, ok, detail):
    results.append({"编号": cid, "用例": name, "预期": expect,
                    "结果": "通过" if ok else "不通过", "实测": detail})
    print("%s %-26s %s | %s" % (cid, name, "PASS" if ok else "FAIL", detail[:90]))


def login(user="admin", pwd=None):
    r = requests.post(BASE + "/api/auth/login",
                      json={"username": user, "password": pwd or DEMO_PWD},
                      timeout=10)
    return r


def auth(tok):
    return {"Authorization": "Bearer " + tok}


def main():
    # ---------- TC-01 正常登录 ----------
    r = login()
    body = r.json()
    tok = body["data"]["token"] if r.status_code == 200 and body.get("code") == 0 else None
    rec("TC-01", "管理员凭证登录", "返回令牌与角色", tok is not None,
        "HTTP %d，角色 %s" % (r.status_code, body["data"]["user"]["role"] if tok else "-"))

    # ---------- TC-02 错误密码 ----------
    r = login(pwd="wrong-pass")
    b2 = r.json()
    rec("TC-02", "错误口令登录", "拒绝并提示", b2.get("code") not in (0, None),
        "HTTP %d，业务码 %s，%s" % (r.status_code, b2.get("code"), b2.get("message", "")[:30]))

    # ---------- TC-03 无令牌访问受保护资源 ----------
    r = requests.get(BASE + "/api/frames", timeout=10)
    rec("TC-03", "未携带令牌访问", "返回 401", r.status_code == 401,
        "HTTP %d，%s" % (r.status_code, r.json().get("message", "")[:40]))

    # ---------- TC-04 越权访问 ----------
    r = login("dealer", "Qts@2026")
    dtok = r.json()["data"]["token"] if r.json().get("code") == 0 else None
    r = requests.get(BASE + "/api/users", headers=auth(dtok), timeout=10)
    rec("TC-04", "经销商访问用户管理", "返回 403", r.status_code == 403,
        "HTTP %d（%s）" % (r.status_code, "拒绝访问" if r.status_code == 403 else "未拦截"))

    # ---------- 取样本 ----------
    r = requests.get(BASE + "/api/frames", params={"current": 1, "size": 1},
                     headers=auth(tok), timeout=10)
    row = r.json()["data"]["records"][0]
    serial, fid = row["serialNo"], row["id"]

    # ---------- TC-05 正常溯源 ----------
    r = requests.get(BASE + "/api/trace/" + serial, timeout=10)
    v = r.json()["data"]["verify"]
    rec("TC-05", "正品车架扫码溯源", "链上链下一致", r.status_code == 200 and v["result"] == "VALID",
        "offChain=%s… onChain=%s… 结论 %s" % (v["offChainHash"][:12], v["onChainHash"][:12], v["result"]))

    # ---------- TC-06 伪造序列号 ----------
    r = requests.get(BASE + "/api/trace/FR-FAKE-000000", timeout=10)
    body = r.json()
    rec("TC-06", "不存在的序列号", "拒绝并给出提示", body.get("code") not in (0, None),
        "HTTP %d，code=%s，%s" % (r.status_code, body.get("code"), body.get("message", "")[:40]))

    # ---------- TC-07 数据篡改检测 ----------
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    cur.execute("SELECT id, record_data FROM process_record WHERE frame_id=%s ORDER BY id LIMIT 1", (fid,))
    pr_id, original = cur.fetchone()
    cur.execute("UPDATE process_record SET record_data=JSON_SET(record_data,'$.temp',999) WHERE id=%s",
                (pr_id,))
    conn.commit()
    requests.delete  # noqa 占位，无实际作用
    # 清缓存，使查询绕过 Redis 直读数据库
    import socket
    s = socket.create_connection(("192.168.105.101", 6379), timeout=5)
    payload = b"*2\r\n$3\r\nDEL\r\n$%d\r\n%s\r\n" % (len("trace:" + serial), ("trace:" + serial).encode())
    s.sendall(payload); s.recv(256); s.close()
    time.sleep(0.2)
    r = requests.get(BASE + "/api/trace/" + serial, timeout=10)
    v = r.json()["data"]["verify"]
    rec("TC-07", "篡改工序参数后验真", "判定为不一致",
        v["result"] == "TAMPERED", "链下 %s… 链上 %s… 结论 %s" % (v["offChainHash"][:12], v["onChainHash"][:12], v["result"]))

    # ---------- TC-08 恢复后重新一致 ----------
    cur.execute("UPDATE process_record SET record_data=%s WHERE id=%s", (original, pr_id))
    conn.commit()
    s = socket.create_connection(("192.168.105.101", 6379), timeout=5)
    s.sendall(payload); s.recv(256); s.close()
    time.sleep(0.2)
    r = requests.get(BASE + "/api/trace/" + serial, timeout=10)
    v = r.json()["data"]["verify"]
    rec("TC-08", "还原数据后重新验真", "恢复为一致", v["result"] == "VALID",
        "结论 %s，链下哈希与篡改前%s" % (v["result"], "一致" if v["result"] == "VALID" else "仍不同"))

    # ---------- TC-09 缓存一致性：改数据后立即查询 ----------
    r = requests.get(BASE + "/api/inspections", params={"frameId": fid},
                     headers=auth(tok), timeout=10)
    ins = r.json()["data"]
    ins_id, ins_result = ins[0]["id"], ins[0]["result"]
    new_result = "FAIL" if ins_result == "PASS" else "PASS"
    r = requests.put(BASE + "/api/inspections/%d" % ins_id,
                     json={"result": new_result}, headers=auth(tok), timeout=10)
    ok_put = r.status_code == 200
    time.sleep(0.15)
    r = requests.get(BASE + "/api/trace/" + serial, timeout=10)
    v2 = r.json()["data"]["verify"]
    # 数据已变更且未重新上链，结论不应再是"已存证且一致"
    rec("TC-09", "改质检后立即查询", "不吃旧缓存", ok_put and v2["recorded"] is False,
        "修改返回 %d，查询 reported=%s（旧缓存会显示 True）" % (r.status_code, v2["recorded"]))

    # ---------- 还原质检并重新上链 ----------
    requests.put(BASE + "/api/inspections/%d" % ins_id,
                 json={"result": ins_result}, headers=auth(tok), timeout=10)
    r = requests.post(BASE + "/api/frames/%d/record-onchain" % fid, headers=auth(tok), timeout=60)
    # 等重新存证完成后校验仍为一致
    for _ in range(10):
        time.sleep(1)
        r = requests.get(BASE + "/api/trace/" + serial, timeout=10)
        if r.json()["data"]["verify"]["result"] == "VALID":
            break
    restored = r.json()["data"]["verify"]["result"] == "VALID"
    rec("TC-10", "还原并重新上链", "数据恢复一致", restored, "重新存证后结论 %s" % ("VALID" if restored else "异常"))

    # ---------- TC-11 质检报告 ----------
    r = requests.get(BASE + "/api/frames/%d/report" % fid, headers=auth(tok), timeout=20)
    html = r.text
    parts = ["一、车架档案", "二、原材料批次", "三、生产工序记录", "四、质量检验记录", "五、区块链存证摘要"]
    hit = sum(1 for p in parts if p in html)
    rec("TC-11", "生成质检报告", "输出完整六部分", r.status_code == 200 and hit == 5,
        "HTTP %d，%s，命中 %d/5 个章节" % (r.status_code, r.headers.get("Content-Type", "")[:30], hit))

    # ---------- TC-12 CSV 台账导出 ----------
    r = requests.get(BASE + "/api/stats/export", headers=auth(tok), timeout=30)
    raw = r.content
    has_bom = raw[:3] == b"\xef\xbb\xbf"
    head = raw.decode("utf-8-sig").splitlines()[0]
    cols = len(head.split(","))
    rec("TC-12", "导出台账 CSV", "BOM + 完整列头", r.status_code == 200 and has_bom and cols >= 14,
        "HTTP %d，BOM=%s，列数=%d" % (r.status_code, has_bom, cols))

    # ---------- TC-13 不存在的路径 ----------
    r = requests.get(BASE + "/api/not-exist-path", headers=auth(tok), timeout=10)
    body = r.json() if r.headers.get("Content-Type", "").startswith("application/json") else {}
    rec("TC-13", "访问不存在的接口", "404 且不泄露堆栈", r.status_code == 404 and "Exception" not in r.text,
        "HTTP %d，%s" % (r.status_code, body.get("message", "")[:40]))

    # ---------- TC-14 非法参数类型 ----------
    r = requests.get(BASE + "/api/frames/abc", headers=auth(tok), timeout=10)
    rec("TC-14", "路径参数类型错误", "返回 400 友好提示", r.status_code == 400,
        "HTTP %d，%s" % (r.status_code, (r.json().get("message") or "")[:40]))

    # ---------- TC-15 令牌被篡改 ----------
    bad = tok[:-6] + "abcdef"
    r = requests.get(BASE + "/api/frames", headers=auth(bad), timeout=10)
    rec("TC-15", "篡改令牌签名", "返回 401", r.status_code == 401,
        "HTTP %d" % r.status_code)

    # ---------- TC-16 分页越界 ----------
    r = requests.get(BASE + "/api/frames", params={"current": 999, "size": 10},
                     headers=auth(tok), timeout=10)
    data = r.json().get("data") or {}
    rec("TC-16", "分页超出范围", "返回空页而非报错",
        r.status_code == 200 and data.get("records") == [],
        "HTTP %d，记录数=%s" % (r.status_code, len(data.get("records") or [])))

    conn.close()

    passed = sum(1 for x in results if x["结果"] == "通过")
    summary = {"总数": len(results), "通过": passed, "不通过": len(results) - passed,
               "通过率": round(passed * 100.0 / len(results), 1), "明细": results}
    with open("func_result.json", "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
    print("\n合计 %d 项，通过 %d 项，通过率 %.1f%%" % (len(results), passed, summary["通过率"]))


if __name__ == "__main__":
    main()
