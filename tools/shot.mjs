// 用 Playwright 对本地原型做可视化自检（截图到 shots/）
import { createRequire } from "node:module";
import fs from "node:fs";

const require = createRequire(import.meta.url);
const { chromium } = require("C:/Users/LMF/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core");

const exe = "C:/Users/LMF/AppData/Local/ms-playwright/chromium-1234/chrome-win64/chrome.exe";
const base = "http://127.0.0.1:8360/";
const out = "D:/projects/paperScan/shots";
fs.mkdirSync(out, { recursive: true });

const errors = [];
const browser = await chromium.launch({ executablePath: exe, headless: true });
const page = await browser.newPage({ viewport: { width: 1180, height: 920 }, deviceScaleFactor: 2 });
page.on("pageerror", e => errors.push("pageerror: " + e.message));
page.on("console", m => { if (m.type() === "error") errors.push("console: " + m.text()); });

await page.goto(base, { waitUntil: "networkidle" });
await page.click("#hintClose");
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/01-feed-dark.png" });

// 进入详情页
await page.click(".card.active");
await page.waitForTimeout(600);
await page.screenshot({ path: out + "/02-detail.png" });

// 用建议问题发起一次 AI 讨论，等待流式回复完成
await page.click('button.chip:has-text("我能用它做什么应用")');
await page.waitForTimeout(2600);
await page.screenshot({ path: out + "/03-chat.png" });

// 返回首页，右滑收藏第一张卡片
await page.click("#detailBack");
await page.waitForTimeout(500);
const feed = await page.$("#feed");
const box = await feed.boundingBox();
const cx = box.x + box.width / 2;
const cy = box.y + box.height / 2;
await page.mouse.move(cx, cy);
await page.mouse.down();
await page.mouse.move(cx + 175, cy, { steps: 12 });
await page.mouse.up();
await page.waitForTimeout(800);

// 打开收藏夹
await page.click("#favBtn");
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/04-bookmarks.png" });

// 返回，打开领域筛选
await page.click("#bmBack");
await page.waitForTimeout(400);
await page.click("#filterBtn");
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/05-filter.png" });
await page.click("#filterBack");
await page.waitForTimeout(400);

// 设置页 + 浅色模式
await page.click('.nav-item[data-view="settings"]');
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/06-settings-dark.png" });
await page.evaluate(() => document.querySelector("#themeToggle").click());
await page.waitForTimeout(500);
await page.click("#stBack");
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/07-feed-light.png" });

await browser.close();
console.log(errors.length ? "ERRORS:\n" + errors.join("\n") : "OK, screenshots saved to shots/");
