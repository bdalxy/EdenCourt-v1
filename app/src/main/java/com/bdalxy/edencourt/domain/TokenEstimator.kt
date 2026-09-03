package com.bdalxy.edencourt.domain

/**
 * Token 统一估算器（v1.5 上下文注入层）。
 *
 * 目的：让「UI 展示的 token 占用」与「注入时的预算截断」用同一个函数，保证显示值 = 实际注入值，
 * 符合可控/可调试/可回溯原则。
 *
 * 精度说明：中文 token 无法本地精确计算（需引入正式 tokenizer，成本高且依赖 API 方分词），
 * 因此沿用「1 字符 ≈ 1 token」的估算口径；UI 标注「估」即可，重点是显示与注入一致。
 */
object TokenEstimator {

    /** 中文粗略 token 估算：1 个字符 ≈ 1 token（够用即可，不引分词器） */
    fun count(text: String): Int = text.length
}