package com.papersnap.app.data

object ChatMock {
    fun reply(paper: Paper, question: String): String {
        val s = paper.summary
        return when {
            question.contains("创新") ->
                "这篇论文的核心贡献可以概括为：$s 从实现上看，创新点主要在于更轻量的架构设计与工程优化，让过去只能靠高算力运行的能力在普通设备上也能实时可用，这是它能产品化的前提。"

            question.contains("对比") || question.contains("强在哪") || question.contains("优势") ->
                "相比此前的方法，它更强调工程可用性：不是单纯刷精度，而是把算力、内存、延迟等运行开销压到真实设备能接受的范围。这类工作特别适合直接拿来做成产品原型或集成进现有工具。"

            question.contains("应用") || question.contains("能做") || question.contains("产品") ->
                "一句话总结已经点明了方向：$s 顺着这个思路，还可以把它扩展成面向医疗、制造、教育等行业的专用工具，或与现有产品结合做成增值功能。"

            question.contains("难度") || question.contains("实现") ->
                "做应用的话难度可控：论文方法偏研究性质，但以开源模型或接口为基础做集成时，重点在量化、裁剪等工程适配，一般一到两个月能出原型；完整复现论文实验则需要较多计算资源。"

            question.contains("数据") || question.contains("训练") ->
                "论文通常使用相关领域的公开数据集训练与评测；复现时建议先看论文的数据准备章节。端侧落地时，还需要针对你自己的场景做少量微调。"

            else ->
                "从摘要来看，这篇论文可以概括为：$s 你可以继续追问方法细节、对比优势或实现难度，我会基于摘要给出可参考的判断。"
        }
    }
}
