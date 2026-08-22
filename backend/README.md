# PaperSnap 内容流水线

每天从 arXiv 拉取最新论文，用 LLM 生成一句话中文总结，输出 App 可直接读取的 JSON。

## 本地运行

```bash
# 1. 配置 Key（可选，不配置则用摘要首句兜底）
copy .env.example .env
# 在 .env 里填写 DEEPSEEK_API_KEY 或 OPENAI_API_KEY

# 2. 运行（Windows PowerShell 加载 .env 或直接设置环境变量）
$env:DEEPSEEK_API_KEY="sk-..."
python backend/papersnap.py
```

输出：`data/papers/YYYY-MM-DD.json` 和 `data/papers/latest.json`。

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| DEEPSEEK_API_KEY / OPENAI_API_KEY | 空 | LLM Key，不配置时总结用摘要首句兜底 |
| LLM_BASE_URL | 按 Key 自动 | 兼容 OpenAI 的 API 地址 |
| LLM_MODEL | deepseek-chat / gpt-4o-mini | 模型名 |
| PS_CATEGORIES | cs.AI,cs.LG,cs.CV,cs.CL,cs.RO,cs.SE | 关注的 arXiv 分类 |
| PS_MAX_PER_CATEGORY | 15 | 每分类抓取上限 |
| PS_MAX_TOTAL | 40 | 每日总量上限 |
| PS_OUTPUT_DIR | data/papers | JSON 输出目录 |
| PS_DATE | 北京时间今天 | 指定生成日期 |

## 每天自动更新（GitHub Actions）

仓库已包含 `.github/workflows/daily-papers.yml`，配置一次后每天 08:00（北京时间）自动运行：

1. 把本项目推送到 GitHub（默认分支）。
2. 在仓库 **Settings → Secrets and variables → Actions** 添加：
   - `DEEPSEEK_API_KEY`（或 `OPENAI_API_KEY` + `LLM_BASE_URL`/`LLM_MODEL`）
3. 在 **Settings → Pages** 选择 "GitHub Actions" 作为发布源（部署 `data/papers`）。
4. 手动触发一次：**Actions → Daily Papers → Run workflow**。

App 里填写的"数据源地址"：

```
https://<你的用户名>.github.io/<仓库名>/latest.json
```

（GitHub Pages 启用后生效；也可以直接用 `https://raw.githubusercontent.com/<用户名>/<仓库名>/main/data/papers/latest.json`）

## 接口格式

`GET /papers/latest.json`（GitHub Pages 部署时即站点根目录）：

```json
{
  "date": "2026-08-22",
  "generated_at": "2026-08-22T08:00:00+08:00",
  "schema_version": 1,
  "total": 40,
  "papers": [
    {
      "arxiv_id": "2608.12345v1",
      "title": "...",
      "title_zh": "",
      "categories": ["cs.AI"],
      "authors": ["..."],
      "published": "...",
      "pdf_url": "https://arxiv.org/abs/2608.12345v1",
      "abstract": "...",
      "summary": "提出一种……，可用来做……。"
    }
  ]
}
```

历史日期：`GET /papers/YYYY-MM-DD.json`。
