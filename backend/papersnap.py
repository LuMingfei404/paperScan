#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PaperSnap 内容流水线：arXiv 抓取 + LLM 一句话总结 -> JSON

用法：
    python backend/papersnap.py

环境变量：
    DEEPSEEK_API_KEY / OPENAI_API_KEY   LLM API Key（不配置则用摘要首句兜底）
    LLM_BASE_URL                         默认 https://api.deepseek.com
    LLM_MODEL                            默认 deepseek-chat
    PS_CATEGORIES                        分类列表，逗号分隔，默认 cs.AI,cs.LG,cs.CV,cs.CL,cs.RO,cs.SE
    PS_MAX_PER_CATEGORY                  每分类最多抓取条数（默认 15）
    PS_MAX_TOTAL                         每日总量上限（默认 40）
    PS_OUTPUT_DIR                        输出目录（默认 data/papers）
    PS_DATE                              指定日期 YYYY-MM-DD（默认北京时间今天）
"""

import json
import os
import re
import ssl
import sys
import time
import urllib.parse
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

# ---------------- 配置 ----------------

CATEGORIES = [c.strip() for c in os.environ.get("PS_CATEGORIES", "cs.AI,cs.LG,cs.CV,cs.CL,cs.RO,cs.SE").split(",") if c.strip()]
MAX_PER_CATEGORY = int(os.environ.get("PS_MAX_PER_CATEGORY", "15"))
MAX_TOTAL = int(os.environ.get("PS_MAX_TOTAL", "40"))
OUTPUT_DIR = os.environ.get("PS_OUTPUT_DIR", "data/papers")
DATE = os.environ.get("PS_DATE", "")
ABSTRACT_MAX = int(os.environ.get("PS_ABSTRACT_MAX", "1800"))

LLM_API_KEY = os.environ.get("DEEPSEEK_API_KEY") or os.environ.get("OPENAI_API_KEY") or ""
LLM_BASE_URL = os.environ.get("LLM_BASE_URL", "")
LLM_MODEL = os.environ.get("LLM_MODEL", "")

if not LLM_BASE_URL:
    LLM_BASE_URL = "https://api.deepseek.com" if os.environ.get("DEEPSEEK_API_KEY") else "https://api.openai.com/v1"
if not LLM_MODEL:
    LLM_MODEL = "deepseek-chat" if os.environ.get("DEEPSEEK_API_KEY") else "gpt-4o-mini"

BEIJING = timezone(timedelta(hours=8))
ARXIV_API = "https://export.arxiv.org/api/query?"
ATOM = {"a": "http://www.w3.org/2005/Atom"}


# ---------------- 工具 ----------------

def beijing_today() -> str:
    return datetime.now(BEIJING).strftime("%Y-%m-%d")


def http_request(url: str, headers=None, data=None, timeout: int = 60) -> bytes:
    """HTTP GET/POST。本地捆绑 Python 可能缺 CA 证书，校验失败时降级为不验证（仅用于公开数据）。"""
    headers = headers or {}
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read()
    except urllib.error.URLError as e:
        if isinstance(getattr(e, "reason", None), ssl.SSLCertVerificationError):
            print("    [警告] 本地证书校验失败，使用不验证证书的降级连接", file=sys.stderr)
            ctx = ssl._create_unverified_context()
            with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
                return resp.read()
        raise


def fetch_arxiv(cat: str, max_results: int) -> list:
    """按分类拉取最新论文元数据（arXiv 官方 API）。"""
    query = urllib.parse.urlencode({
        "search_query": f"cat:{cat}",
        "sortBy": "submittedDate",
        "sortOrder": "descending",
        "start": 0,
        "max_results": max_results,
    })
    url = ARXIV_API + query
    xml_bytes = http_request(url, timeout=30)
    root = ET.fromstring(xml_bytes)
    papers = []
    for entry in root.findall("a:entry", ATOM):
        aid = (entry.findtext("a:id", default="", namespaces=ATOM) or "").strip()
        arxiv_id = aid.rsplit("/abs/", 1)[-1].strip()
        if not arxiv_id:
            continue
        title = " ".join((entry.findtext("a:title", default="", namespaces=ATOM) or "").split())
        abstract = " ".join((entry.findtext("a:summary", default="", namespaces=ATOM) or "").split())[:ABSTRACT_MAX]
        published = (entry.findtext("a:published", default="", namespaces=ATOM) or "").strip()
        authors = [a.text.strip() for a in entry.findall("a:author/a:name", ATOM) if a.text]
        categories = [c.get("term", "") for c in entry.findall("a:category", ATOM) if c.get("term")]
        papers.append({
            "arxiv_id": arxiv_id,
            "title": title,
            "title_zh": "",
            "categories": categories,
            "authors": authors[:12],
            "published": published,
            "pdf_url": f"https://arxiv.org/abs/{arxiv_id}",
            "abstract": abstract,
            "summary": "",
        })
    return papers


def fallback_summary(abstract: str) -> str:
    """无 LLM 或调用失败时，用摘要首句兜底。"""
    s = abstract.strip()
    if not s:
        return "（摘要为空）"
    first = s.split(". ")[0]
    if not first.endswith("."):
        first += "."
    return first[:140]


def llm_summarize(title: str, abstract: str) -> str:
    """调用 LLM 生成一句话中文总结。"""
    prompt = (
        "你是一名资深技术产品经理。根据下面论文的标题和摘要，用最通俗的中文写一句话总结（不超过80字）：\n"
        "要求：像摘要一样自然通顺，一句话同时点明论文解决了什么问题、能用来开发什么应用；"
        "禁止分条列举、禁止学术套话。\n"
        "严格输出 JSON：{\"summary\":\"...\"}\n"
        f"标题：{title}\n摘要：{abstract}"
    )
    url = LLM_BASE_URL.rstrip("/") + "/chat/completions"
    payload = {
        "model": LLM_MODEL,
        "temperature": 0.3,
        "messages": [{"role": "user", "content": prompt}],
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {LLM_API_KEY}",
    }
    for attempt in range(2):
        body = dict(payload)
        if attempt == 0:
            body["response_format"] = {"type": "json_object"}
        try:
            data = json.loads(http_request(url, headers=headers, data=json.dumps(body).encode("utf-8"), timeout=60).decode("utf-8"))
            content = data["choices"][0]["message"]["content"]
            match = re.search(r"\{.*\}", content, re.S)
            if match:
                parsed = json.loads(match.group(0))
                summary = parsed.get("summary", "").strip()
                if summary:
                    return summary[:120]
        except Exception as e:
            if attempt == 1:
                print(f"    [LLM 失败] {e}")
        time.sleep(1.5)
    raise RuntimeError("LLM summarize failed")


def run() -> None:
    date = DATE or beijing_today()
    print(f"[PaperSnap] 日期: {date} | 分类: {', '.join(CATEGORIES)} | LLM: {LLM_MODEL if LLM_API_KEY else '未配置(兜底摘要)'}")

    # 1. 抓取（跨分类去重）
    seen = {}
    for cat in CATEGORIES:
        try:
            papers = fetch_arxiv(cat, MAX_PER_CATEGORY * 2)
            print(f"  {cat}: 拉到 {len(papers)} 篇")
            for p in papers:
                if p["arxiv_id"] not in seen:
                    seen[p["arxiv_id"]] = p
        except Exception as e:
            print(f"  {cat}: 抓取失败 {e}")
        time.sleep(1.5)  # arXiv 限速要求

    # 均衡选择：每个分类先取若干篇，再按时间补充到总量上限
    per_cat = max(1, MAX_TOTAL // max(1, len(CATEGORIES)))
    selected, used = [], set()
    for cat in CATEGORIES:
        cands = sorted(
            (p for p in seen.values() if cat in p["categories"]),
            key=lambda p: p["published"],
            reverse=True,
        )
        for p in cands:
            if len(selected) >= per_cat:
                break
            if p["arxiv_id"] not in used:
                used.add(p["arxiv_id"])
                selected.append(p)
    rest = sorted(
        (p for p in seen.values() if p["arxiv_id"] not in used),
        key=lambda p: p["published"],
        reverse=True,
    )
    for p in rest:
        if len(selected) >= MAX_TOTAL:
            break
        used.add(p["arxiv_id"])
        selected.append(p)
    papers = sorted(selected, key=lambda p: p["published"], reverse=True)
    print(f"去重后共 {len(papers)} 篇（分类覆盖: {sorted({c for p in papers for c in p['categories']})}）")

    # 2. LLM 提炼（失败兜底）
    for i, p in enumerate(papers, 1):
        if LLM_API_KEY:
            try:
                p["summary"] = llm_summarize(p["title"], p["abstract"])
            except Exception:
                p["summary"] = fallback_summary(p["abstract"])
        else:
            p["summary"] = fallback_summary(p["abstract"])
        print(f"  {i}/{len(papers)} {p['arxiv_id']} | {p['summary'][:42]}")

    # 3. 输出 JSON
    payload = {
        "date": date,
        "generated_at": datetime.now(BEIJING).isoformat(timespec="seconds"),
        "schema_version": 1,
        "total": len(papers),
        "papers": papers,
    }
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    date_file = os.path.join(OUTPUT_DIR, f"{date}.json")
    latest_file = os.path.join(OUTPUT_DIR, "latest.json")
    for path in (date_file, latest_file):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"完成：{date_file} ({len(papers)} 篇)")


if __name__ == "__main__":
    try:
        run()
    except KeyboardInterrupt:
        sys.exit(130)
