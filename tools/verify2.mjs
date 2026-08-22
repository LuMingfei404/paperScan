// v2 交互冒烟测试：滑动、已读移除、浏览记录、设置项
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

// 滚轮翻页
let c = await feedBox();
await page.mouse.move(c.x, c.y);
await page.mouse.wheel(0, 300);
await page.waitForTimeout(500);
console.log("wheel down:", await counter());
await page.mouse.wheel(0, -300);
await page.waitForTimeout(500);
console.log("wheel up:", await counter());

// 右滑收藏（带背景提示），应前进到下一篇
c = await feedBox();
await page.mouse.move(c.x, c.y);
await page.mouse.down();
await page.mouse.move(c.x + 175, c.y, { steps: 14 });
await page.mouse.up();
await page.waitForTimeout(800);
console.log("after right-swipe bookmark:", await counter());

// 点击卡片 = 已读并移出论文池
await page.click(".card.active");
await page.waitForTimeout(500);
const activeView = await page.evaluate(() => document.querySelector(".view.active")?.dataset.view);
console.log("detail view:", activeView);
await page.click("#detailBack");
await page.waitForTimeout(500);
console.log("after read-removal:", await counter());

// 浏览记录应有 1 条
await page.click('.nav-item[data-view="history"]');
await page.waitForTimeout(400);
console.log("history items:", await page.$$eval("#hsList .bm-card", els => els.length));
await page.click("#hsBack");
await page.waitForTimeout(300);

// 设置：数量步进 + 发表时间筛选
await page.click('.nav-item[data-view="settings"]');
await page.waitForTimeout(400);
await page.click("#poolMinus");
await page.waitForTimeout(200);
console.log("pool input value:", await page.inputValue("#poolInput"));
await page.click('#timeRangeChips .chip[data-v="30"]');
await page.waitForTimeout(300);
await page.click("#stBack");
await page.waitForTimeout(400);
console.log("after 近1天 filter:", await counter());

// 返回键：进入详情后浏览器后退应回到首页
await page.click(".card.active");
await page.waitForTimeout(400);
await page.goBack();
await page.waitForTimeout(400);
const backView = await page.evaluate(() => document.querySelector(".view.active")?.dataset.view);
console.log("back key returns to:", backView);

console.log("errors:", errors.length ? errors.join(" | ") : "none");
await browser.close();
