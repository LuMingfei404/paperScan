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

// 右滑收藏第一篇（带背景提示）
const feed = await page.$("#feed");
const box = await feed.boundingBox();
const cx = box.x + box.width / 2;
const cy = box.y + box.height / 2;
await page.mouse.move(cx, cy);
await page.mouse.down();
await page.mouse.move(cx + 175, cy, { steps: 14 });
await page.mouse.up();
await page.waitForTimeout(800);

// 点击卡片 → 全屏聊天详情页
await page.click(".card.active");
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/02-detail-chat.png" });

// 打开论文信息浮层
await page.click("#infoBtn");
await page.waitForTimeout(400);
await page.screenshot({ path: out + "/03-paper-sheet.png" });
await page.click("#sheetMask", { position: { x: 10, y: 10 } });
await page.waitForTimeout(300);

// 发送一条建议问题
await page.click('button.chip:has-text("我能用它做什么应用")');
await page.waitForTimeout(2600);
await page.screenshot({ path: out + "/04-chat.png" });

// 返回首页
await page.click("#detailBack");
await page.waitForTimeout(500);

// 打开收藏夹
await page.click('.nav-item[data-view="bookmarks"]');
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/05-bookmarks.png" });
await page.click("#bmBack");
await page.waitForTimeout(300);

// 浏览记录
await page.click('.nav-item[data-view="history"]');
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/06-history.png" });
await page.click("#hsBack");
await page.waitForTimeout(300);

// 设置页
await page.click('.nav-item[data-view="settings"]');
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/07-settings.png" });
await page.evaluate(() => document.querySelector("#themeToggle").click());
await page.waitForTimeout(500);
await page.click("#stBack");
await page.waitForTimeout(500);
await page.screenshot({ path: out + "/08-feed-light.png" });

await browser.close();
console.log(errors.length ? "ERRORS:\n" + errors.join("\n") : "OK, screenshots saved to shots/");
