// PaperSnap 原型模拟数据（与真实 JSON 接口字段一致）

const PAPERS = {
  "2608.1001": {
    arxiv_id: "2608.1001",
    title: "Efficient On-Device Diffusion Model for Real-time Video Super-resolution and Stylization",
    title_zh: "手机端实时视频超分与风格化的高效端侧扩散模型",
    categories: ["cs.CV", "cs.AI"],
    authors: ["Alice Zhang", "Bo Li", "Chen Wang"],
    published: "2026-08-21T17:02:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1001",
    abstract: "We present a lightweight diffusion architecture tailored for mobile GPUs, combining a cascaded latent refinement stage with a distilled denoising scheduler. The model achieves real-time 720p video super-resolution and style transfer on commodity smartphones while reducing peak memory by 78% compared to prior on-device diffusion approaches.",
    summary: "提出一种轻量扩散架构，让手机端也能实时完成 720p 视频超分与风格化，可用来开发视频增强相机或直播滤镜。"
  },
  "2608.1002": {
    arxiv_id: "2608.1002",
    title: "LightViT-Glass: A Sub-100ms Vision Transformer for Smart Glasses",
    title_zh: "LightViT-Glass：面向智能眼镜的亚百毫秒轻量视觉 Transformer",
    categories: ["cs.CV", "cs.AI"],
    authors: ["Ming Chen", "Sofia Ruiz"],
    published: "2026-08-21T18:20:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1002",
    abstract: "We design a vision transformer with hierarchical token pruning and quantized attention, running at 93ms per frame on a smart-glasses SoC. Our method preserves accuracy within 1.8% of full-precision ViT-B while using 4x less energy, enabling always-on scene understanding for wearable devices.",
    summary: "通过令牌剪枝与量化注意力，把视觉 Transformer 压缩到智能眼镜可常开运行的程度，可支撑 AR 导航与实时场景理解类应用。"
  },
  "2608.1003": {
    arxiv_id: "2608.1003",
    title: "GaussianForge: Fast Generative 3D Asset Reconstruction from Single Images",
    title_zh: "GaussianForge：单图快速生成式 3D 资产重建",
    categories: ["cs.CV", "cs.GR"],
    authors: ["Yuki Tanaka", "Lei Sun"],
    published: "2026-08-21T19:10:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1003",
    abstract: "We introduce a feed-forward 3D Gaussian reconstruction model that turns a single photograph into a relightable 3D asset in under 0.5 seconds on consumer hardware. A generative prior fills occluded regions and produces physically-based materials, closing the gap between reconstruction and production-ready assets.",
    summary: "实现从单张照片 0.5 秒生成可重新照明的 3D 资产，游戏资产制作和电商 3D 展示可以直接拿来用。"
  },
  "2608.1004": {
    arxiv_id: "2608.1004",
    title: "Federated Learning with Adaptive Communication Pruning for IoT Devices",
    title_zh: "面向 IoT 设备的自适应通信裁剪联邦学习",
    categories: ["cs.LG", "cs.AI"],
    authors: ["Priya Nair", "Jorge Almeida"],
    published: "2026-08-21T20:05:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1004",
    abstract: "We propose a communication-efficient federated learning framework that adaptively prunes gradient updates per device based on channel quality and model sensitivity. Evaluations on smart-home sensor networks show 72% less uplink traffic with negligible accuracy loss, making on-device personalization practical under bandwidth constraints.",
    summary: "用自适应通信裁剪把联邦学习的流量开销降低 72%，让智能家居等 IoT 设备也能做隐私保护的个性化模型。"
  },
  "2608.1005": {
    arxiv_id: "2608.1005",
    title: "Streaming Multimodal LLMs for Long-Form Video Understanding on Phones",
    title_zh: "手机端长视频理解的高效流式多模态大模型",
    categories: ["cs.CV", "cs.CL", "cs.AI"],
    authors: ["Hannah Lee", "Omar Farouk", "Xin Zhao"],
    published: "2026-08-21T21:14:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1005",
    abstract: "We introduce a streaming architecture that processes hours of video with constant memory by hierarchically summarizing visual tokens and scheduling inference across idle compute windows. The model answers long-horizon questions with 89% accuracy on a new benchmark while fitting within a 6GB phone memory budget.",
    summary: "用分层摘要与空闲窗口调度实现恒内存的长视频理解，可在手机端做视频问答与监控录像异常摘要。"
  },
  "2608.1006": {
    arxiv_id: "2608.1006",
    title: "RepoRAG: Retrieval-Augmented Generation over Whole Code Repositories",
    title_zh: "RepoRAG：面向整个代码仓库的检索增强生成",
    categories: ["cs.SE", "cs.AI"],
    authors: ["David Kim", "Anna Petrov"],
    published: "2026-08-20T16:40:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1006",
    abstract: "We build a repository-level retrieval framework that combines symbol graphs with semantic chunking, improving answer accuracy on codebase questions from 61% to 87% over naive RAG. The system handles cross-file dependencies and stale-documentation drift in large monorepos.",
    summary: "把符号图与语义切块结合，让代码问答在跨文件的仓库级问题上准确率从 61% 提升到 87%，适合做团队代码助手。"
  },
  "2608.1007": {
    arxiv_id: "2608.1007",
    title: "DeXtreme: Dexterous Hand Grasping via Contrastive Tactile Representation Learning",
    title_zh: "DeXtreme：基于对比触觉表征学习的灵巧手抓取策略",
    categories: ["cs.RO", "cs.AI"],
    authors: ["Tomás Herrera", "Wei Lu"],
    published: "2026-08-20T18:55:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1007",
    abstract: "We train dexterous grasping policies with a contrastive representation that aligns visual, proprioceptive, and tactile signals, enabling sim-to-real transfer on a low-cost 16-DOF hand. The policy generalizes to novel objects with 31% higher success than vision-only baselines and adapts in under 30 minutes of real-world fine-tuning.",
    summary: "用对比触觉表征让低成本灵巧手在陌生物体上的抓取成功率提升 31%，可用于机器人捡货与仿生假肢控制。"
  },
  "2608.1008": {
    arxiv_id: "2608.1008",
    title: "SparseQ: Sub-4-Bit LLM Quantization with Adaptive Outlier Routing",
    title_zh: "SparseQ：自适应离群路由的亚 4-bit 大模型量化",
    categories: ["cs.LG", "cs.AI"],
    authors: ["Elena Rossi", "Karan Shah"],
    published: "2026-08-20T20:30:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1008",
    abstract: "We propose a quantization scheme that routes weight outliers to a small dense sub-network, achieving 3.2-bit effective precision with under 1% perplexity degradation on 7B models. Our method cuts memory footprint by 3.4x and doubles token throughput on mobile NPUs without retraining.",
    summary: "把大模型量化到 3.2bit 仍能保持精度，内存减少 3.4 倍，手机端离线助手与本地翻译工具可以直接受益。"
  },
  "2608.1009": {
    arxiv_id: "2608.1009",
    title: "CtrlDiff: Training-Free Controllable Editing for Diffusion Image Generators",
    title_zh: "CtrlDiff：扩散图像生成器的免训练可控编辑",
    categories: ["cs.CV", "cs.GR"],
    authors: ["Rui Chen", "Nina Kovacs"],
    published: "2026-08-19T15:22:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1009",
    abstract: "We introduce a training-free attention reweighting mechanism that gives fine-grained control over layout, style, and subject identity in existing diffusion models. The method requires no fine-tuning or extra modules, works with any prompt-only API, and reduces editing artifacts by 43% on user studies.",
    summary: "用免训练的重加权机制让现有扩散模型可精确控制布局与风格，可做成高级修图或设计素材批量风格化工具。"
  },
  "2608.1010": {
    arxiv_id: "2608.1010",
    title: "Few-shot Medical Image Segmentation via Anatomical Prototype Memory",
    title_zh: "基于解剖原型记忆的医学影像小样本分割",
    categories: ["cs.CV"],
    authors: ["Fatima Al-Sayed", "Peter Novak"],
    published: "2026-08-19T17:48:00Z",
    pdf_url: "https://arxiv.org/abs/2608.1010",
    abstract: "We develop a few-shot segmentation framework with a prototypical memory bank that encodes anatomical priors, enabling accurate organ and lesion segmentation from only 5 annotated examples. Validation on CT and MRI datasets shows Dice improvements of 9-14 points over meta-learning baselines, with strong robustness to domain shift across scanners.",
    summary: "用解剖原型记忆让仅 5 张标注就能训练医学影像分割模型，适合影像科辅助标注与基层筛查设备。"
  }
};

const DATES = {
  "2026-08-22": {
    ids: ["2608.1001", "2608.1002", "2608.1003", "2608.1004", "2608.1005", "2608.1006"]
  },
  "2026-08-21": {
    ids: ["2608.1006", "2608.1007", "2608.1008", "2608.1009"]
  },
  "2026-08-20": {
    ids: ["2608.1009", "2608.1010"]
  }
};

const ALL_CATEGORIES = ["cs.AI", "cs.CV", "cs.CL", "cs.LG", "cs.RO", "cs.SE", "cs.GR"];

// 建议问题
const SUGGESTED_QUESTIONS = [
  "这篇论文的核心创新点是什么？",
  "和之前的方法比，强在哪里？",
  "我能用它做什么应用？",
  "实现难度有多大？"
];
