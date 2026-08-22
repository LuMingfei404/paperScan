// 交互冒烟测试：滚轮翻页、左滑隐藏、切换日期、领域筛选
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require("C:/Users/LMF/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core");

const exe = "C:/Users/LMF/AppData/Local/ms-playwright/chromium-1234/chrome-win64/chrome.exe";
const browser = await chromium.launch({ executablePath: exe, headless: true });
const page = await browser.newPage({ viewport: { width: 1180, height: 920 } });
const errors = [];
page.on("pageerror", e => errors.push("pageerror: " + e.message));
page.on("console", m => { if (m.type() === "error") errors.push("console: " + m.text()); });

await page.goto("http://127.0.0.1:8360/", { waitUntil: "networkidle" });
await page.click("#hintClose");
await page.waitForTimeout(400);

const counter = () => page.textContent("#feedCounter");
const feedBox = async () => {
  const b = await (await page.$("#feed")).boundingBox();
  return { x: b.x + b.width / 2, y: b.y + b.height / 2 };
};

console.log("start:", await counter());

// 滚轮向下翻 2 篇
let c = await feedBox();
await page.mouse.move(c.x, c.y);
await page.mouse.wheel(0, 300);
await page.waitForTimeout(500);
console.log("wheel down 1:", await counter());
await page.mouse.wheel(0, 300);
await page.waitForTimeout(500);
console.log("wheel down 2:", await counter());

// 滚轮向上返回
await page.mouse.wheel(0, -300);
await page.waitForTimeout(500);
console.log("wheel up:", await counter());

// 左滑隐藏第一篇
c = await feedBox();
await page.mouse.move(c.x, c.y);
await page.mouse.down();
await page.mouse.move(c.x - 175, c.y, { steps: 12 });
await page.mouse.up();
await page.waitForTimeout(800);
console.log("after hide:", await counter());

// 切换日期到 2026-08-21
await page.click("#dateBtn");
await page.click('.date-opt[data-date="2026-08-21"]');
await page.waitForTimeout(500);
console.log("date switch:", await counter(), "|", (await page.textContent("#dateBtn")).trim());

// 打开筛选，勾选 cs.CV，确认
await page.click("#filterBtn");
await page.click('.cat-chip[data-cat="cs.CV"]');
await page.click("#filterConfirm");
await page.waitForTimeout(500);
console.log("after filter cs.CV:", await counter());

console.log("errors:", errors.length ? errors.join(" | ") : "none");
await browser.close();
