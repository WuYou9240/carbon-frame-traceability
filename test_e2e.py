# -*- coding: utf-8 -*-
"""端到端流程测试：录入→工艺→质检→上链→扫码验真→篡改检测"""
import json
import time
import urllib.request

BASE = "http://localhost:18080/api"
SERIAL = "FR-TEST-" + str(int(time.time()))

def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=60) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {"code": e.code, "message": e.reason, "data": None}

def login(user, pwd="Qts@2026"):
    r = call("POST", "/auth/login", {"username": user, "password": pwd})
    assert r["code"] == 0, r
    return r["data"]["token"]

def check(name, cond, detail=""):
    print(("✅" if cond else "❌") + " " + name + ("  " + str(detail) if detail else ""))
    if not cond:
        raise SystemExit(1)

# 批次号也要唯一
BATCH_NO = "T700-" + str(int(time.time()))

# 1. 登录
admin = login("admin")
operator = login("operator")
qc = login("qc")
dealer = login("dealer")
check("四角色登录", True)

# 2. 新增原材料批次
r = call("POST", "/materials", {
    "batchNo": BATCH_NO, "materialName": "T700 碳纤维预浸料",
    "spec": "12K/200g/树脂含量32%", "supplier": "东丽中国", "incomingDate": "2026-10-20",
    "qcStatus": "PASSED"}, operator)
check("新增原材料批次", r["code"] == 0, r["data"]["id"])

# 3. 新建车架
r = call("POST", "/frames", {
    "serialNo": SERIAL, "model": "Aero-R1", "size": "52cm",
    "batchId": r["data"]["id"], "workshop": "一号车间", "productionDate": "2026-10-25"}, operator)
check("新建车架档案", r["code"] == 0 and r.get("data"), r)
frame_id = r["data"]["id"]

# 4. 录入 6 道工序
processes = [
    ("CLOTH_SELECTION", {"材料型号": "T700-12K", "树脂含量": "32%", "来料批次": "T700-2026-1026"}),
    ("AUTO_CUTTING", {"裁布层数": 24, "单层尺寸": "600x300mm", "设备编号": "CUT-02"}),
    ("LAYUP", {"铺层角度": [0, 45, 90, 45, 0], "层数": 24, "作业人员": "王师傅"}),
    ("CURING", {"温度曲线": "80C/1h→130C/2h→160C/3h", "固化压力": "0.6MPa", "固化炉号": "AUTOCLAVE-1"}),
    ("CNC", {"加工精度": "±0.05mm", "五轴程序": "NC-8821"}),
    ("NDT", {"检测方式": "超声波C扫描", "孔隙率": "1.2%", "结果": "无分层"}),
]
for ptype, params in processes:
    r = call("POST", f"/frames/{frame_id}/processes",
             {"processType": ptype, "recordData": json.dumps(params, ensure_ascii=False)}, operator)
    assert r["code"] == 0, r
check("录入 6 道生产工序", True)

# 5. 权限校验：经销商不能录工序
r = call("POST", f"/frames/{frame_id}/processes",
         {"processType": "CNC", "recordData": "{}"}, dealer)
check("经销商录工序被拒绝(403)", r["code"] != 0, r.get("code"))

# 6. 质检三项全部合格 → 自动上链
for item, desc in [("APPEARANCE", "表面光洁无划痕"), ("DIMENSION", "五通同轴度0.02mm"), ("MECHANIC", "头管扭矩测试通过")]:
    r = call("POST", "/inspections",
             {"frameId": frame_id, "item": item, "result": "PASS", "defectDesc": desc}, qc)
    assert r["code"] == 0, r
check("三项质检合格录入", True)

# 7. 查档案确认已上链
r = call("GET", f"/frames/{frame_id}", token=admin)
frame = r["data"]["frame"]
check("质检完成后自动上链", frame["hashRecorded"] == 1, "currentHash=" + str(frame["currentHash"])[:16] + "...")

# 8. 公开扫码溯源 + 验真
r = call("GET", f"/trace/{SERIAL}")
verify = r["data"]["verify"]
check("扫码溯源查询", r["code"] == 0 and r["data"]["frame"]["serialNo"] == SERIAL)
check("哈希验真通过(VALID)", verify["result"] == "VALID", verify["message"])

# 9. 篡改检测：修改链下数据 → 验真应报警
r = call("PUT", f"/frames/{frame_id}", {"model": "Aero-R1-TAMPERED"}, admin)
r = call("GET", f"/trace/{SERIAL}")
verify = r["data"]["verify"]
check("篡改后验真报警(TAMPERED)", verify["result"] == "TAMPERED", verify["message"])

# 10. 二维码
req = urllib.request.Request(BASE + f"/qr/{SERIAL}.png")
with urllib.request.urlopen(req, timeout=10) as resp:
    png = resp.read()
check("二维码 PNG 生成", png[:4] == b"\x89PNG", f"{len(png)} bytes")

# 11. 统计 + 上链记录 + 操作日志
r = call("GET", "/stats/overview", token=admin)
check("统计分析接口", r["code"] == 0 and r["data"]["frameTotal"] >= 1)
r = call("GET", "/chain/records", token=admin)
check("上链交易记录", r["data"]["total"] >= 1, r["data"]["records"][0]["txHash"][:24] + "...")
r = call("GET", "/logs", token=admin)
check("操作日志", r["data"]["total"] >= 1)

print("\n=== 全部端到端测试通过 ===")
