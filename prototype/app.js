/* PaperSnap 论文快闪 · 交互原型逻辑（v2：论文池 + 已读 + 浏览记录） */
(function () {
  "use strict";

  const $ = (s, el) => (el || document).querySelector(s);
  const $$ = (s, el) => Array.from((el || document).querySelectorAll(s));

  const store = {
    get(key, fallback) {
      try {
        const v = localStorage.getItem("ps_" + key);
        return v === null ? fallback : JSON.parse(v);
      } catch (e) { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem("ps_" + key, JSON.stringify(value)); } catch (e) {}
    }
  };

  const state = {
    date: "2026-08-22",
    index: 0,
    list: [],
    currentId: null,
    detailId: null,
    returnView: "feed",
    categories: store.get("cats", null),
    bookmarks: store.get("bms", []),
    read: store.get("read", []),          // [{id, at}]
    poolSize: Math.min(100, Math.max(1, store.get("pool", 40))),
    timeRange: store.get("trange", 0),    // 0=不限, 1/3/7 天
    theme: store.get("theme", "dark"),
    hintSeen: store.get("hint", false)
  };

  const CATEGORY_LABELS = {
    "cs.AI": "人工智能",
    "cs.CV": "计算机视觉",
    "cs.CL": "自然语言处理",
    "cs.LG": "机器学习",
    "cs.RO": "机器人",
    "cs.SE": "软件工程",
    "cs.GR": "计算机图形学"
  };

  function catLabel(c) { return CATEGORY_LABELS[c] ? c + " " + CATEGORY_LABELS[c] : c; }

  let chatBusy = false;
  let lastNav = 0;
  let navStack = ["feed"];

  /* ---------------- 通用 ---------------- */

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  let toastTimer = null;
  function toast(msg) {
    const t = $("#toast");
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove("show"), 2200);
  }

  function paperById(id) { return PAPERS[id] || null; }
  function bookmarked(id) { return state.bookmarks.indexOf(id) >= 0; }
  function readIds() { return new Set(state.read.map(r => r.id)); }

  function toggleBookmark(id) {
    const i = state.bookmarks.indexOf(id);
    if (i >= 0) state.bookmarks.splice(i, 1);
    else state.bookmarks.push(id);
    store.set("bms", state.bookmarks);
    return i < 0;
  }

  function dateTs(dateStr) {
    return new Date(dateStr + "T00:00:00+08:00").getTime();
  }

  function hoursAgo(id) {
    const i = state.list.indexOf(id);
    return (i >= 0 ? i + 1 : 1) * 3 + "h";
  }

  /* ---------------- 论文池 ---------------- */

  function buildPool() {
    const ids = DATES[state.date].ids;
    const cats = state.categories;
    const days = state.timeRange;
    const cutoff = days > 0 ? dateTs(state.date) - days * 86400000 : 0;
    const read = readIds();
    const pool = [];
    for (const id of ids) {
      if (pool.length >= state.poolSize) break;
      const p = PAPERS[id];
      if (read.has(id)) continue;
      if (cats && cats.length && !p.categories.some(c => cats.indexOf(c) >= 0)) continue;
      if (days > 0 && new Date(p.published).getTime() < cutoff) continue;
      pool.push(id);
    }
    state.list = pool;
    if (state.index > state.list.length - 1) state.index = Math.max(0, state.list.length - 1);
    if (state.index < 0) state.index = 0;
  }

  function removeFromList(id) {
    const i = state.list.indexOf(id);
    if (i >= 0) {
      state.list.splice(i, 1);
      if (state.index >= state.list.length) state.index = Math.max(0, state.list.length - 1);
    }
  }

  function markRead(id) {
    if (readIds().has(id)) return false;
    state.read.unshift({ id: id, at: Date.now() });
    store.set("read", state.read);
    return true;
  }

  /* ---------------- 视图切换 ---------------- */

  function showView(name) {
    if (name === "feed") {
      buildPool();
      if (state.currentId) {
        const i = state.list.indexOf(state.currentId);
        if (i >= 0) state.index = i;
      }
    }
    activateView(name);
    if (navStack[navStack.length - 1] !== name) {
      navStack.push(name);
      history.pushState({ nav: navStack.length }, "");
    }
  }

  function activateView(name) {
    $$(".view").forEach(v => v.classList.toggle("active", v.dataset.view === name));
    $$(".nav-item").forEach(n => n.classList.toggle("active", n.dataset.view === name));
    if (name === "feed") { buildPool(); renderFeed(); }
    if (name === "bookmarks") renderBookmarks();
    if (name === "history") renderHistory();
  }

  window.addEventListener("popstate", () => {
    if (navStack.length > 1) {
      navStack.pop();
      activateView(navStack[navStack.length - 1]);
    } else {
      toast("已是首页（真机按返回将退出 App）");
    }
  });

  /* ---------------- 首页卡片流 ---------------- */

  function cardHTML(p, isNext) {
    const cats = p.categories.map(c => `<span class="tag">${escapeHtml(catLabel(c))}</span>`).join("");
    const heart = bookmarked(p.arxiv_id) ? "♥" : "♡";
    const heartCls = bookmarked(p.arxiv_id) ? "on" : "";
    return `
      <article class="card ${isNext ? "next" : "active"}" data-id="${p.arxiv_id}">
        <div class="swipe-hint">♥ 收藏</div>
        <div class="card-tags">
          ${cats}<span class="tag ghost">${hoursAgo(p.arxiv_id)}前</span>
          <span class="card-id">arXiv:${escapeHtml(p.arxiv_id)}</span>
        </div>
        <h2 class="card-title">${escapeHtml(p.title_zh)}</h2>
        <div class="card-title-en">${escapeHtml(p.title)}</div>
        <p class="card-summary">${escapeHtml(p.summary)}</p>
        <div class="card-actions">
          <button class="outline-btn" data-act="pdf">查看原文 ↗</button>
          <button class="heart-btn ${heartCls}" data-act="heart" aria-label="收藏">${heart}</button>
        </div>
      </article>`;
  }

  function endCardHTML() {
    return `
      <article class="card next end-card">
        <div class="state-icon">🎉</div>
        <div class="state-title">今天看完了</div>
        <div class="state-desc">共 ${state.list.length} 篇 · 已读论文会自动移出论文池</div>
      </article>`;
  }

  function renderFeed(dir) {
    const wrap = $("#feedCards");
    const counter = $("#feedCounter");

    if (!state.list.length) {
      wrap.innerHTML = `
        <div class="state-card">
          <div class="state-icon">🗂️</div>
          <div class="state-title">论文池已清空</div>
          <div class="state-desc">已读论文已自动移出。可刷新论文池，或调整筛选条件。</div>
          <button class="outline-btn" id="refreshPool" type="button">刷新论文池</button>
          <button class="outline-btn" id="gotoHistory" type="button">查看浏览记录</button>
        </div>`;
      $("#refreshPool").addEventListener("click", () => {
        buildPool(); renderFeed();
        toast(state.list.length ? `论文池刷新：${state.list.length} 篇` : "暂无更多新论文，可调整筛选");
      });
      $("#gotoHistory").addEventListener("click", () => showView("history"));
      counter.textContent = "0 / 0";
      return;
    }

    const i = state.index;
    const cur = paperById(state.list[i]);
    state.currentId = cur ? cur.arxiv_id : state.currentId;
    const isLast = i >= state.list.length - 1;
    counter.textContent = (i + 1) + " / " + state.list.length;
    wrap.innerHTML = cardHTML(cur, false) + (isLast ? endCardHTML() : cardHTML(paperById(state.list[i + 1]), true));

    const active = $(".card.active", wrap);
    if (active && dir) {
      active.classList.add("dragging");
      active.style.transform = dir === "up" ? "translateY(80px)" : "translateY(-80px)";
      void active.offsetWidth;
      active.classList.remove("dragging");
      active.style.transform = "";
    }

    bindCardListeners(wrap);
  }

  function bindCardListeners(wrap) {
    $$(".card", wrap).forEach(card => {
      card.addEventListener("click", e => {
        const btn = e.target.closest("[data-act]");
        if (btn) {
          const p = paperById(card.dataset.id);
          if (btn.dataset.act === "pdf") { window.open(p.pdf_url, "_blank"); return; }
          if (btn.dataset.act === "heart") {
            const on = toggleBookmark(p.arxiv_id);
            btn.textContent = on ? "♥" : "♡";
            btn.classList.toggle("on", on);
            toast(on ? "已收藏 ♥ 灵感 +1" : "已取消收藏");
          }
          return;
        }
        if (card.classList.contains("active")) openDetail(card.dataset.id, "feed");
      });
    });
  }

  function nextCard() {
    if (Date.now() - lastNav < 260) return;
    lastNav = Date.now();
    if (state.index >= state.list.length - 1) { toast("已经是今天最后一篇 🎉"); return; }
    state.index++;
    renderFeed("up");
  }

  function prevCard() {
    if (Date.now() - lastNav < 260) return;
    lastNav = Date.now();
    if (state.index <= 0) { toast("已经是第一篇了"); return; }
    state.index--;
    renderFeed("down");
  }

  function animateOut(dir, done) {
    const active = $(".card.active", $("#feedCards"));
    if (!active) { done(); return; }
    active.classList.add("dragging");
    active.style.transition = "transform .22s ease, opacity .22s ease";
    active.style.transform = dir === "right" ? "translateX(115%) rotate(8deg)" : "translateX(-115%) rotate(-8deg)";
    active.style.opacity = "0";
    setTimeout(() => { active.remove(); done(); }, 220);
  }

  function doBookmark(p) {
    if (bookmarked(p.arxiv_id)) {
      toast("已在收藏夹，点卡片 ♥ 可取消");
    } else {
      toggleBookmark(p.arxiv_id);
      toast("已收藏 ♥ 灵感 +1");
    }
    animateOut("right", () => {
      if (state.index >= state.list.length - 1) renderFeed();
      else { state.index++; renderFeed("up"); }
    });
  }

  /* ---------------- 卡片手势（优化：滑动阈值 + 速度 + 顺滑回弹） ---------------- */

  function bindFeedGestures() {
    const feed = $("#feed");
    const wrap = $("#feedCards");
    let drag = null;

    feed.addEventListener("pointerdown", e => {
      if (e.target.closest("button")) return;
      const active = $(".card.active", wrap);
      if (!active) return;
      drag = {
        x: e.clientX, y: e.clientY, dx: 0, dy: 0, mode: null,
        active, moves: [], t0: performance.now()
      };
      feed.setPointerCapture(e.pointerId);
      active.classList.add("dragging");
    });

    feed.addEventListener("pointermove", e => {
      if (!drag) return;
      const now = performance.now();
      drag.dx = e.clientX - drag.x;
      drag.dy = e.clientY - drag.y;
      drag.moves.push({ t: now, x: e.clientX, y: e.clientY });
      if (drag.moves.length > 8) drag.moves.shift();

      if (!drag.mode && (Math.abs(drag.dx) > 10 || Math.abs(drag.dy) > 10)) {
        drag.mode = Math.abs(drag.dx) > Math.abs(drag.dy) * 1.15 ? "h" : "v";
      }

      const a = drag.active;
      const next = $(".card.next", wrap);
      const hint = $(".swipe-hint", a);
      if (drag.mode === "h") {
        // 只允许向右滑；左滑不动
        const x = Math.max(0, drag.dx);
        a.style.transform = `translateX(${x}px) rotate(${x * 0.035}deg)`;
        if (hint) hint.classList.toggle("visible", x > 24);
        // 右滑时隐藏背景的下一篇论文
        if (next) {
          next.style.opacity = "0";
          next.style.transform = "translateY(60px)";
        }
      } else if (drag.mode === "v") {
        a.style.transform = `translateY(${drag.dy}px) rotate(${drag.dy * 0.015}deg)`;
        if (next) {
          const H = feed.clientHeight;
          const prog = Math.max(-1, Math.min(1, drag.dy / H));
          next.style.transform = `translateY(${48 + drag.dy * 0.45}px) scale(${0.96 + Math.abs(prog) * 0.04})`;
          next.style.opacity = String(0.8 + Math.abs(prog) * 0.2);
        }
      }
    });

    function velocity(moves, axis) {
      const m = moves;
      if (m.length < 2) return 0;
      const a = m[m.length - 1];
      const b = m[m.length - 2];
      const dt = Math.max(1, a.t - b.t);
      return (a[axis] - b[axis]) / dt; // px/ms
    }

    function finish() {
      if (!drag) return;
      const d = drag;
      drag = null;
      const a = d.active;
      const p = paperById(a.dataset.id);
      a.classList.remove("dragging");
      const hint = $(".swipe-hint", a);
      if (hint) hint.classList.remove("visible");
      const next = $(".card.next", wrap);

      // 轻点 → 进入详情（标记已读）
      if (!d.mode && Math.abs(d.dx) < 10 && Math.abs(d.dy) < 10) {
        a.style.transform = "";
        openDetail(p.arxiv_id, "feed");
        return;
      }

      if (d.mode === "h") {
        const vx = velocity(d.moves, "x");
        if (d.dx > 120 || (d.dx > 55 && vx > 0.55)) { doBookmark(p); return; }
        a.style.transition = "transform .3s cubic-bezier(.2,.8,.25,1)";
        a.style.transform = "";
        if (next) { next.style.transition = "opacity .25s ease, transform .25s ease"; next.style.opacity = ""; next.style.transform = ""; }
        setTimeout(() => { a.style.transition = ""; }, 320);
        return;
      }

      if (d.mode === "v") {
        const vy = velocity(d.moves, "y");
        if (d.dy < -110 || (d.dy < -55 && vy < -0.55)) { nextCard(); return; }
        if (d.dy > 110) { prevCard(); return; }
        a.style.transition = "transform .3s cubic-bezier(.2,.8,.25,1)";
        a.style.transform = "";
        if (next) { next.style.transition = "transform .3s cubic-bezier(.2,.8,.25,1), opacity .3s ease"; next.style.transform = ""; next.style.opacity = ""; }
        setTimeout(() => { a.style.transition = ""; if (next) next.style.transition = ""; }, 320);
        return;
      }

      a.style.transform = "";
    }

    feed.addEventListener("pointerup", finish);
    feed.addEventListener("pointercancel", finish);

    feed.addEventListener("wheel", e => {
      e.preventDefault();
      if (e.deltaY > 18) nextCard();
      else if (e.deltaY < -18) prevCard();
    }, { passive: false });
  }

  window.addEventListener("keydown", e => {
    if (!$("#viewFeed").classList.contains("active")) return;
    if (document.activeElement && /input|textarea/.test(document.activeElement.tagName)) return;
    if (e.key === "ArrowDown") { e.preventDefault(); nextCard(); }
    if (e.key === "ArrowUp") { e.preventDefault(); prevCard(); }
  });

  /* ---------------- 领域筛选页 ---------------- */

  let draftCats = [];

  function openFilter() {
    draftCats = state.categories ? state.categories.slice() : [];
    renderChips();
    showView("filter");
  }

  function renderChips() {
    $("#chipGrid").innerHTML = ALL_CATEGORIES.map(c =>
      `<button class="cat-chip ${draftCats.indexOf(c) >= 0 ? "on" : ""}" data-cat="${c}" type="button">${escapeHtml(catLabel(c))}</button>`
    ).join("");
    $$(".cat-chip").forEach(ch => ch.addEventListener("click", () => {
      const c = ch.dataset.cat;
      const i = draftCats.indexOf(c);
      if (i >= 0) draftCats.splice(i, 1); else draftCats.push(c);
      renderChips();
    }));
  }

  function saveCats() {
    state.categories = draftCats.length ? draftCats : null;
    store.set("cats", state.categories);
    state.index = 0;
    toast(state.categories ? "已保存 " + state.categories.length + " 个领域" : "已保存：显示全部领域");
    buildPool(); renderFeed(); showView("feed");
  }

  /* ---------------- 设置页：论文类型 / 发表时间 / 数量 ---------------- */

  function renderSettingsCats() {
    const box = $("#catChipsSettings");
    const cats = state.categories || [];
    box.innerHTML = ALL_CATEGORIES.map(c =>
      `<button class="cat-chip ${cats.indexOf(c) >= 0 ? "on" : ""}" data-cat="${c}" type="button">${escapeHtml(catLabel(c))}</button>`
    ).join("");
    $$(".cat-chip", box).forEach(ch => ch.addEventListener("click", () => {
      const c = ch.dataset.cat;
      const cur = state.categories ? state.categories.slice() : [];
      const i = cur.indexOf(c);
      if (i >= 0) cur.splice(i, 1); else cur.push(c);
      state.categories = cur.length ? cur : null;
      store.set("cats", state.categories);
      renderSettingsCats();
      buildPool();
      toast(state.categories ? "论文类型：" + state.categories.length + " 个" : "论文类型：全部");
    }));
  }

  const TIME_OPTIONS = [
    { v: 0, label: "不限" },
    { v: 30, label: "近1个月" },
    { v: 90, label: "近3个月" },
    { v: 180, label: "近6个月" },
    { v: 365, label: "近1年" }
  ];

  function renderTimeRange() {
    const box = $("#timeRangeChips");
    box.innerHTML = TIME_OPTIONS.map(o =>
      `<button class="chip ${state.timeRange === o.v ? "on" : ""}" data-v="${o.v}" type="button">${o.label}</button>`
    ).join("");
    $$(".chip", box).forEach(ch => ch.addEventListener("click", () => {
      state.timeRange = Number(ch.dataset.v);
      store.set("trange", state.timeRange);
      renderTimeRange();
      buildPool();
      toast("发表时间：" + TIME_OPTIONS.find(o => o.v === state.timeRange).label);
    }));
  }

  function clampPool(v) {
    return Math.min(100, Math.max(1, Math.round(v) || 40));
  }

  function renderPoolInput() {
    $("#poolInput").value = state.poolSize;
  }

  function setPoolSize(v) {
    state.poolSize = clampPool(v);
    store.set("pool", state.poolSize);
    renderPoolInput();
    buildPool();
    toast("每天论文数量：" + state.poolSize + " 篇");
  }

  /* ---------------- 详情页（全屏聊天 + 论文信息浮层） ---------------- */

  function openDetail(id, from) {
    const p = paperById(id);
    if (!p) return;
    markRead(id);
    removeFromList(id);
    state.detailId = id;
    state.returnView = from || "feed";
    renderChat();
    showView("detail");
  }

  function fillSheet(p) {
    const body = $("#sheetBody");
    body.innerHTML = `
      <div class="sheet-tags">${p.categories.map(c => `<span class="tag">${escapeHtml(catLabel(c))}</span>`).join("")}
        <span class="tag ghost">arXiv:${escapeHtml(p.arxiv_id)}</span></div>
      <div class="sheet-title">${escapeHtml(p.title_zh)}</div>
      <div class="sheet-title-en">${escapeHtml(p.title)}</div>
      <div class="sheet-meta">${escapeHtml(p.authors.join("、"))} · ${escapeHtml(p.published.slice(0, 10))}</div>
      <div class="sheet-summary">📄 ${escapeHtml(p.summary)}</div>
      <div class="sheet-abstract"><b>摘要</b><br>${escapeHtml(p.abstract)}</div>
      <div class="sheet-btns">
        <button class="outline-btn" id="sheetPdf" type="button">查看原文 ↗</button>
        <button class="outline-btn" id="sheetCopy" type="button">复制总结</button>
      </div>`;
    $("#sheetPdf").addEventListener("click", () => window.open(p.pdf_url, "_blank"));
    $("#sheetCopy").addEventListener("click", () => {
      const done = () => toast("已复制一句话总结");
      if (navigator.clipboard) navigator.clipboard.writeText(p.summary).then(done).catch(done);
      else { const ta = document.createElement("textarea"); ta.value = p.summary; document.body.appendChild(ta); ta.select(); document.execCommand("copy"); ta.remove(); done(); }
    });
    $("#sheetMask").classList.remove("hidden");
  }

  /* ---------------- AI 讨论 ---------------- */

  function chatKey(id) { return "chat_" + id; }
  function getHistory(id) { return store.get(chatKey(id), []); }
  function saveHistory(id, msgs) { store.set(chatKey(id), msgs); }

  function scrollChatBottom() {
    const box = $("#chatMsgs");
    if (box) box.scrollTop = box.scrollHeight;
  }

  function renderChat() {
    const id = state.detailId;
    const msgs = getHistory(id);
    $("#chatMsgs").innerHTML = msgs.map(m =>
      `<div class="msg ${m.role === "user" ? "user" : "ai"}">${escapeHtml(m.content)}</div>`).join("");
    renderChatChips();
    scrollChatBottom();
  }

  function renderChatChips() {
    const id = state.detailId;
    const asked = new Set(getHistory(id).filter(m => m.role === "user").map(m => m.content));
    $("#chatChips").innerHTML = SUGGESTED_QUESTIONS.map(q =>
      `<button class="chip" data-q="${escapeHtml(q)}" type="button" ${asked.has(q) ? "disabled style='opacity:.4'" : ""}>${escapeHtml(q)}</button>`
    ).join("");
    $$(".chip", $("#chatChips")).forEach(b => b.addEventListener("click", () => sendMessage(b.dataset.q)));
  }

  function mockReply(p, q) {
    if (q.indexOf("创新") >= 0) {
      return "这篇论文的核心贡献可以概括为：" + p.summary + " 从实现上看，创新点主要在于更轻量的架构设计与工程优化，让过去只能靠高算力运行的能力在普通设备上也能实时可用，这是它能产品化的前提。";
    }
    if (q.indexOf("对比") >= 0 || q.indexOf("强在哪") >= 0 || q.indexOf("优势") >= 0) {
      return "相比此前的方法，它更强调工程可用性：不是单纯刷精度，而是把算力、内存、延迟等运行开销压到真实设备能接受的范围。这类工作特别适合直接拿来做成产品原型或集成进现有工具。";
    }
    if (q.indexOf("应用") >= 0 || q.indexOf("能做") >= 0 || q.indexOf("产品") >= 0) {
      return "一句话总结已经点明了方向：" + p.summary + " 顺着这个思路，还可以把它扩展成面向医疗、制造、教育等行业的专用工具，或与现有产品结合做成增值功能。";
    }
    if (q.indexOf("难度") >= 0 || q.indexOf("实现") >= 0) {
      return "做应用的话难度可控：论文方法偏研究性质，但以开源模型或接口为基础做集成时，重点在量化、裁剪等工程适配，一般一到两个月能出原型；完整复现论文实验则需要较多计算资源。";
    }
    if (q.indexOf("数据") >= 0 || q.indexOf("训练") >= 0) {
      return "论文通常使用相关领域的公开数据集训练与评测；复现时建议先看论文的数据准备章节。端侧落地时，还需要针对你自己的场景做少量微调。";
    }
    return "从摘要来看，这篇论文可以概括为：" + p.summary + " 你可以继续追问方法细节、对比优势或实现难度，我会基于摘要给出可参考的判断。";
  }

  function appendMsg(role, html) {
    const el = document.createElement("div");
    el.className = "msg " + role;
    el.innerHTML = html;
    $("#chatMsgs").appendChild(el);
    scrollChatBottom();
  }

  function sendMessage(text) {
    text = (text || "").trim();
    if (!text || chatBusy || !state.detailId) return;
    const id = state.detailId;
    const history = getHistory(id);
    history.push({ role: "user", content: text });
    saveHistory(id, history);
    appendMsg("user", escapeHtml(text));
    $("#chatInput").value = "";
    chatBusy = true;

    const typing = document.createElement("div");
    typing.className = "msg ai";
    typing.innerHTML = '<span class="typing"><i></i><i></i><i></i></span>';
    $("#chatMsgs").appendChild(typing);
    scrollChatBottom();

    const reply = mockReply(paperById(id), text);
    setTimeout(() => {
      typing.remove();
      const bubble = document.createElement("div");
      bubble.className = "msg ai";
      $("#chatMsgs").appendChild(bubble);
      let i = 0;
      const iv = setInterval(() => {
        i += 2;
        bubble.textContent = reply.slice(0, i);
        scrollChatBottom();
        if (i >= reply.length) {
          clearInterval(iv);
          chatBusy = false;
          history.push({ role: "assistant", content: reply });
          saveHistory(id, history);
          renderChatChips();
        }
      }, 24);
    }, 650 + Math.random() * 500);
  }

  /* ---------------- 收藏夹 ---------------- */

  function renderBookmarks() {
    const q = ($("#bmSearch").value || "").trim().toLowerCase();
    const list = state.bookmarks.map(paperById).filter(Boolean);
    const filtered = q
      ? list.filter(p => (p.title_zh + p.title + p.summary).toLowerCase().indexOf(q) >= 0)
      : list;
    const box = $("#bmList");
    if (!filtered.length) {
      box.innerHTML = `<div class="empty">${list.length ? "没有匹配的收藏" : "还没有收藏 ♡\n\n在首页右滑卡片，或点卡片上的 ♥ 即可收藏"}</div>`;
      return;
    }
    box.innerHTML = filtered.map(p => `
      <div class="bm-card" data-id="${p.arxiv_id}">
        <div class="t">${escapeHtml(p.title_zh)}</div>
        <div class="s">📄 ${escapeHtml(p.summary)}</div>
        <div class="bm-foot">
          <span class="bm-cats">${p.categories.map(c => escapeHtml(c)).join(" · ")}</span>
          <button class="bm-rm" type="button">♥ 取消收藏</button>
        </div>
      </div>`).join("");
    $$(".bm-card", box).forEach(card => {
      card.addEventListener("click", e => {
        if (e.target.closest(".bm-rm")) {
          const id = card.dataset.id;
          const i = state.bookmarks.indexOf(id);
          if (i >= 0) state.bookmarks.splice(i, 1);
          store.set("bms", state.bookmarks);
          renderBookmarks();
          toast("已取消收藏");
          return;
        }
        openDetail(card.dataset.id, "bookmarks");
      });
    });
  }

  /* ---------------- 浏览记录 ---------------- */

  function renderHistory() {
    const q = ($("#hsSearch").value || "").trim().toLowerCase();
    const items = state.read.map(r => ({ r: r, p: paperById(r.id) })).filter(x => x.p);
    const filtered = q
      ? items.filter(x => (x.p.title_zh + x.p.title + x.p.summary).toLowerCase().indexOf(q) >= 0)
      : items;
    const box = $("#hsList");
    if (!filtered.length) {
      box.innerHTML = `<div class="empty">${state.read.length ? "没有匹配的记录" : "还没有浏览记录\n\n点击论文卡片阅读后会自动记录，并移出论文池"}</div>`;
      return;
    }
    box.innerHTML = filtered.map(x => {
      const when = new Date(x.r.at).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
      return `
      <div class="bm-card" data-id="${x.p.arxiv_id}">
        <div class="t">${escapeHtml(x.p.title_zh)}</div>
        <div class="s">📄 ${escapeHtml(x.p.summary)}</div>
        <div class="bm-foot">
          <span class="bm-cats">已读 ${when} · ${x.p.categories.map(c => escapeHtml(c)).join(" · ")}</span>
          <button class="bm-rm hs-rm" type="button">✕ 移除</button>
        </div>
      </div>`;
    }).join("");
    $$(".bm-card", box).forEach(card => {
      card.addEventListener("click", e => {
        if (e.target.closest(".hs-rm")) {
          const id = card.dataset.id;
          state.read = state.read.filter(r => r.id !== id);
          store.set("read", state.read);
          renderHistory();
          toast("已移除（该论文将回到论文池）");
          return;
        }
        openDetail(card.dataset.id, "history");
      });
    });
  }

  /* ---------------- 设置 ---------------- */

  function applyTheme() {
    $("#phone").dataset.theme = state.theme;
    $("#themeToggle").checked = state.theme === "dark";
  }

  /* ---------------- 事件绑定 ---------------- */

  function bindEvents() {
    $$(".nav-item").forEach(n => n.addEventListener("click", () => showView(n.dataset.view)));
    $("#filterBtn").addEventListener("click", openFilter);
    $("#refreshBtn").addEventListener("click", () => {
      buildPool(); renderFeed();
      toast("论文池已刷新：" + state.list.length + " 篇未读");
    });

    $("#filterBack").addEventListener("click", () => showView("feed"));
    $("#filterSave").addEventListener("click", saveCats);
    $("#filterConfirm").addEventListener("click", saveCats);

    // 详情页：全屏聊天 + 信息浮层
    $("#detailBack").addEventListener("click", () => showView(state.returnView));
    $("#detailShare").addEventListener("click", () => {
      const p = paperById(state.detailId);
      if (p) {
        const done = () => toast("已复制一句话总结，可粘贴分享");
        if (navigator.clipboard) navigator.clipboard.writeText(p.summary).then(done).catch(done);
        else toast("分享功能在真机版可用");
      }
    });
    $("#infoBtn").addEventListener("click", () => {
      const p = paperById(state.detailId);
      if (p) fillSheet(p);
    });
    $("#sheetMask").addEventListener("click", e => {
      if (e.target === $("#sheetMask")) $("#sheetMask").classList.add("hidden");
    });

    // 收藏夹
    $("#bmBack").addEventListener("click", () => showView("feed"));
    $("#bmShare").addEventListener("click", () => toast("原型演示：将分享整个收藏列表"));
    $("#bmSearch").addEventListener("input", renderBookmarks);

    // 浏览记录
    $("#hsBack").addEventListener("click", () => showView("feed"));
    $("#hsClear").addEventListener("click", () => {
      state.read = [];
      store.set("read", state.read);
      renderHistory();
      buildPool();
      toast("浏览记录已清空，论文将重新进入论文池");
    });
    $("#hsSearch").addEventListener("input", renderHistory);

    // 设置
    $("#stBack").addEventListener("click", () => showView("feed"));
    $("#themeToggle").addEventListener("change", e => {
      state.theme = e.target.checked ? "dark" : "light";
      store.set("theme", state.theme);
      applyTheme();
      toast(state.theme === "dark" ? "已切换深色模式" : "已切换浅色模式");
    });
    $("#poolMinus").addEventListener("click", () => setPoolSize(state.poolSize - 10));
    $("#poolPlus").addEventListener("click", () => setPoolSize(state.poolSize + 10));
    $("#poolInput").addEventListener("change", e => setPoolSize(Number(e.target.value)));
    $("#clearCacheRow").addEventListener("click", () => {
      const keys = Object.keys(localStorage).filter(k => k.indexOf("ps_chat_") === 0);
      keys.forEach(k => localStorage.removeItem(k));
      state.read = [];
      store.set("read", state.read);
      toast("已清理对话记录与浏览记录");
    });

    // 聊天
    $("#chatSend").addEventListener("click", () => sendMessage($("#chatInput").value));
    $("#chatInput").addEventListener("keydown", e => {
      if (e.key === "Enter") sendMessage($("#chatInput").value);
    });

    $("#hintClose").addEventListener("click", () => {
      $("#hint").classList.add("hidden");
      store.set("hint", true);
    });
  }

  /* ---------------- 初始化 ---------------- */

  function init() {
    // 浏览记录默认保留 7 天，超期自动清理
    const cutoff = Date.now() - 7 * 86400000;
    const pruned = state.read.filter(r => r.at >= cutoff);
    if (pruned.length !== state.read.length) {
      state.read = pruned;
      store.set("read", state.read);
    }
    history.replaceState({ nav: 1 }, "");
    applyTheme();
    renderSettingsCats();
    renderTimeRange();
    renderPoolInput();
    buildPool();
    renderFeed();
    bindFeedGestures();
    bindEvents();
    if (state.hintSeen) $("#hint").classList.add("hidden");
  }

  init();
})();
