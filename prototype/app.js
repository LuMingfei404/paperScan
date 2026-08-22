/* PaperSnap 论文快闪 · 交互原型逻辑 */
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
    detailId: null,
    returnView: "feed",
    categories: store.get("cats", null),
    bookmarks: store.get("bms", []),
    hidden: store.get("hidden", []),
    theme: store.get("theme", "dark"),
    hintSeen: store.get("hint", false)
  };

  let chatBusy = false;
  let lastNav = 0;

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

  function toggleBookmark(id) {
    const i = state.bookmarks.indexOf(id);
    if (i >= 0) state.bookmarks.splice(i, 1);
    else state.bookmarks.push(id);
    store.set("bms", state.bookmarks);
    return i < 0;
  }

  function hoursAgo(id) {
    const i = state.list.indexOf(id);
    return (i >= 0 ? i + 1 : 1) * 3 + "h";
  }

  /* ---------------- 视图切换 ---------------- */

  function showView(name) {
    $$(".view").forEach(v => v.classList.toggle("active", v.dataset.view === name));
    $$(".nav-item").forEach(n => n.classList.toggle("active", n.dataset.view === name));
    if (name === "feed") { buildList(); renderFeed(); }
    if (name === "bookmarks") renderBookmarks();
  }

  /* ---------------- 首页卡片流 ---------------- */

  function buildList() {
    const ids = DATES[state.date].ids;
    const cats = state.categories;
    state.list = ids.filter(id => {
      if (state.hidden.indexOf(id) >= 0) return false;
      const p = PAPERS[id];
      if (cats && cats.length) return p.categories.some(c => cats.indexOf(c) >= 0);
      return true;
    });
    if (state.index > state.list.length - 1) state.index = Math.max(0, state.list.length - 1);
    if (state.index < 0) state.index = 0;
  }

  function cardHTML(p, isNext) {
    const cats = p.categories.map(c => `<span class="tag">${escapeHtml(c)}</span>`).join("");
    const heart = bookmarked(p.arxiv_id) ? "♥" : "♡";
    const heartCls = bookmarked(p.arxiv_id) ? "on" : "";
    return `
      <article class="card ${isNext ? "next" : "active"}" data-id="${p.arxiv_id}">
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
        <div class="state-desc">共 ${state.list.length} 篇 · 明天见。下拉刷新可重新拉取。</div>
      </article>`;
  }

  function renderFeed(dir) {
    const wrap = $("#feedCards");
    const counter = $("#feedCounter");

    if (!state.list.length) {
      wrap.innerHTML = `
        <div class="state-card">
          <div class="state-icon">🗂️</div>
          <div class="state-title">当天没有可显示的论文</div>
          <div class="state-desc">可能是领域筛选太窄，或论文已被全部隐藏</div>
          <button class="outline-btn" id="restoreAll" type="button">恢复全部隐藏论文</button>
        </div>`;
      $("#restoreAll").addEventListener("click", () => {
        state.hidden = [];
        store.set("hidden", state.hidden);
        buildList(); renderFeed();
        toast("已恢复全部隐藏论文");
      });
      counter.textContent = "0 / 0";
      return;
    }

    const i = state.index;
    const cur = paperById(state.list[i]);
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
    if (state.index >= state.list.length - 1) {
      toast("已经是今天最后一篇 🎉");
      return;
    }
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
    const on = toggleBookmark(p.arxiv_id);
    toast(on ? "已收藏 ♥ 灵感 +1" : "已取消收藏");
    animateOut("right", () => {
      if (state.index >= state.list.length - 1) renderFeed();
      else { state.index++; renderFeed("up"); }
    });
  }

  function doHide(p) {
    state.hidden.push(p.arxiv_id);
    store.set("hidden", state.hidden);
    toast("已隐藏，今天不再显示");
    animateOut("left", () => {
      buildList();
      renderFeed();
    });
  }

  /* ---------------- 卡片手势 ---------------- */

  function bindFeedGestures() {
    const feed = $("#feed");
    let drag = null;

    feed.addEventListener("pointerdown", e => {
      if (e.target.closest("button")) return;
      const active = $(".card.active", $("#feedCards"));
      if (!active) return;
      drag = { x: e.clientX, y: e.clientY, dx: 0, dy: 0, mode: null, active };
      feed.setPointerCapture(e.pointerId);
      active.classList.add("dragging");
    });

    feed.addEventListener("pointermove", e => {
      if (!drag) return;
      drag.dx = e.clientX - drag.x;
      drag.dy = e.clientY - drag.y;
      if (!drag.mode && (Math.abs(drag.dx) > 8 || Math.abs(drag.dy) > 8)) {
        drag.mode = Math.abs(drag.dx) > Math.abs(drag.dy) * 1.15 ? "h" : "v";
      }
      const H = feed.clientHeight;
      const a = drag.active;
      const next = $(".card.next", $("#feedCards"));
      if (drag.mode === "h") {
        a.style.transform = `translateX(${drag.dx}px) rotate(${drag.dx * 0.045}deg)`;
      } else if (drag.mode === "v") {
        a.style.transform = `translateY(${drag.dy}px) rotate(${drag.dy * 0.02}deg)`;
        if (next) {
          const prog = Math.max(-1, Math.min(1, drag.dy / H));
          next.style.transform = `translateY(${48 + drag.dy * 0.45}px) scale(${0.96 + Math.abs(prog) * 0.04})`;
          next.style.opacity = String(0.8 + Math.abs(prog) * 0.2);
        }
      }
    });

    function finish(e) {
      if (!drag) return;
      const d = drag;
      drag = null;
      const a = d.active;
      const p = paperById(a.dataset.id);
      a.classList.remove("dragging");
      const next = $(".card.next", $("#feedCards"));

      // 轻点卡片 → 进入详情页
      if (!d.mode && Math.abs(d.dx) < 8 && Math.abs(d.dy) < 8) {
        a.style.transform = "";
        openDetail(p.arxiv_id, "feed");
        return;
      }

      if (d.mode === "h") {
        if (d.dx > 110) { doBookmark(p); return; }
        if (d.dx < -110) { doHide(p); return; }
        a.style.transform = "";
        return;
      }
      if (d.mode === "v") {
        if (d.dy < -90) { nextCard(); return; }
        if (d.dy > 90) { prevCard(); return; }
        a.style.transform = "";
        if (next) { next.style.transform = ""; next.style.opacity = ""; }
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

  /* ---------------- 日期切换 ---------------- */

  function renderDateMenu() {
    const menu = $("#dateMenu");
    const labels = { "2026-08-22": "今天", "2026-08-21": "昨天", "2026-08-20": "前天" };
    menu.innerHTML = Object.keys(DATES).map(d => `
      <button class="date-opt" data-date="${d}" type="button">
        ${d}${labels[d] ? `<span class="tag">${labels[d]}</span>` : ""}
      </button>`).join("");
    $$(".date-opt", menu).forEach(b => b.addEventListener("click", () => {
      state.date = b.dataset.date;
      state.index = 0;
      $("#dateBtn").innerHTML = state.date + " <i>▾</i>";
      menu.classList.add("hidden");
      buildList(); renderFeed();
      toast("已切换到 " + state.date);
    }));
  }

  /* ---------------- 领域筛选 ---------------- */

  let draftCats = [];

  function openFilter() {
    draftCats = state.categories ? state.categories.slice() : [];
    renderChips();
    showView("filter");
  }

  function renderChips() {
    $("#chipGrid").innerHTML = ALL_CATEGORIES.map(c =>
      `<button class="cat-chip ${draftCats.indexOf(c) >= 0 ? "on" : ""}" data-cat="${c}" type="button">${escapeHtml(c)}</button>`
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
    buildList(); renderFeed(); showView("feed");
  }

  /* ---------------- 详情页 ---------------- */

  function detailHTML(p) {
    const cats = p.categories.map(c => `<span class="tag">${escapeHtml(c)}</span>`).join("");
    return `
      <div class="detail-tags">${cats}<span class="tag ghost">arXiv:${escapeHtml(p.arxiv_id)}</span></div>
      <h1 class="detail-title">${escapeHtml(p.title_zh)}</h1>
      <div class="detail-title-en">${escapeHtml(p.title)}</div>
      <div class="detail-meta">${escapeHtml(p.authors.join("、"))} · ${escapeHtml(p.published.slice(0, 10))}</div>
      <div class="detail-section">
        <div class="blk"><div class="blk-head">📄 一句话总结</div><div class="blk-body">${escapeHtml(p.summary)}</div></div>
      </div>
      <div class="detail-abstract" id="abstractBox">
        <div class="blk-head">摘要</div>
        <div id="abstractText">${escapeHtml(p.abstract)}</div>
        <button class="abstract-fold" id="abstractFold" type="button">展开全文</button>
      </div>
      <div class="detail-btns">
        <button class="outline-btn" id="pdfBtn" type="button">查看 PDF ↗</button>
        <button class="outline-btn" id="copyBtn" type="button">复制总结</button>
      </div>`;
  }

  function openDetail(id, from) {
    const p = paperById(id);
    if (!p) return;
    state.detailId = id;
    state.returnView = from || "feed";
    $("#detailBody").innerHTML = detailHTML(p);

    let folded = true;
    const box = $("#abstractBox");
    const text = $("#abstractText");
    const foldBtn = $("#abstractFold");
    text.classList.add("folded");
    foldBtn.addEventListener("click", () => {
      folded = !folded;
      text.classList.toggle("folded", folded);
      foldBtn.textContent = folded ? "展开全文" : "收起";
    });

    $("#pdfBtn").addEventListener("click", () => window.open(p.pdf_url, "_blank"));
    $("#copyBtn").addEventListener("click", () => {
      const s = p.summary;
      const done = () => toast("已复制问题+应用总结");
      if (navigator.clipboard) navigator.clipboard.writeText(s).then(done).catch(done);
      else { const ta = document.createElement("textarea"); ta.value = s; document.body.appendChild(ta); ta.select(); document.execCommand("copy"); ta.remove(); done(); }
    });

    renderChat();
    showView("detail");
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

  /* ---------------- 设置 ---------------- */

  function applyTheme() {
    $("#phone").dataset.theme = state.theme;
    $("#themeToggle").checked = state.theme === "dark";
  }

  /* ---------------- 事件绑定 ---------------- */

  function bindEvents() {
    // 底部导航
    $$(".nav-item").forEach(n => n.addEventListener("click", () => showView(n.dataset.view)));
    $("#favBtn").addEventListener("click", () => showView("bookmarks"));
    $("#filterBtn").addEventListener("click", openFilter);
    $("#refreshBtn").addEventListener("click", () => {
      toast("已刷新，当前为最新数据");
      renderFeed();
    });

    // 日期菜单
    $("#dateBtn").addEventListener("click", e => {
      e.stopPropagation();
      $("#dateMenu").classList.toggle("hidden");
    });
    document.addEventListener("click", () => $("#dateMenu").classList.add("hidden"));

    // 筛选页
    $("#filterBack").addEventListener("click", () => showView("feed"));
    $("#filterSave").addEventListener("click", saveCats);
    $("#filterConfirm").addEventListener("click", saveCats);

    // 详情页
    $("#detailBack").addEventListener("click", () => showView(state.returnView));
    $("#detailShare").addEventListener("click", () => toast("原型演示：将生成精美分享卡片"));

    // 收藏夹
    $("#bmBack").addEventListener("click", () => showView("feed"));
    $("#bmShare").addEventListener("click", () => toast("原型演示：将分享整个收藏列表"));
    $("#bmSearch").addEventListener("input", renderBookmarks);

    // 设置
    $("#stBack").addEventListener("click", () => showView("feed"));
    $("#themeToggle").addEventListener("change", e => {
      state.theme = e.target.checked ? "dark" : "light";
      store.set("theme", state.theme);
      applyTheme();
      toast(state.theme === "dark" ? "已切换深色模式" : "已切换浅色模式");
    });
    $("#clearCacheRow").addEventListener("click", () => {
      const keys = Object.keys(localStorage).filter(k => k.indexOf("ps_chat_") === 0);
      keys.forEach(k => localStorage.removeItem(k));
      state.hidden = [];
      store.set("hidden", state.hidden);
      toast("已清理对话记录与隐藏记录");
    });

    // 聊天
    $("#chatSend").addEventListener("click", () => sendMessage($("#chatInput").value));
    $("#chatInput").addEventListener("keydown", e => {
      if (e.key === "Enter") sendMessage($("#chatInput").value);
    });

    // 欢迎浮层
    $("#hintClose").addEventListener("click", () => {
      $("#hint").classList.add("hidden");
      store.set("hint", true);
    });
  }

  /* ---------------- 初始化 ---------------- */

  function init() {
    applyTheme();
    renderDateMenu();
    buildList();
    renderFeed();
    bindFeedGestures();
    bindEvents();
    if (state.hintSeen) $("#hint").classList.add("hidden");
  }

  init();
})();
