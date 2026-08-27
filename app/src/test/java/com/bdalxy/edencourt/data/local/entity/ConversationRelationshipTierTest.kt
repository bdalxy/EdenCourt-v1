package com.bdalxy.edencourt.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 好感度分档与归属测试（方案 G-③ 回归集 Phase1）。
 * 保护 T11.6 好感度数值体系：单场加成、数值→关系标签、数值→中文档位名。
 * 任何改动导致档位边界漂移/错档都会在此失败。
 */
class ConversationRelationshipTierTest {

    // ---- 好感度加成（T11.6：破冰 10 + 每回合 2，封顶 5 回合）----

    @Test
    fun `affinityGain_破冰加成为10`() {
        assertEquals(10, ConversationRelationship.affinityGain(0))
    }

    @Test
    fun `affinityGain_每回合加2_且封顶5回合`() {
        assertEquals(12, ConversationRelationship.affinityGain(1))
        assertEquals(20, ConversationRelationship.affinityGain(5))
        // 超过 5 回合不再增加，仍按 5 回合计（10 + 2*5）
        assertEquals(20, ConversationRelationship.affinityGain(10))
    }

    // ---- 数值 → 关系标签（阈值：陌生<20 / 相识<40 / 友好<70 / 亲密>=70）----

    @Test
    fun `relationStatusForAffinity_陌生档`() {
        assertEquals(RelationStatus.STRANGER.name, ConversationRelationship.relationStatusForAffinity(0))
        assertEquals(RelationStatus.STRANGER.name, ConversationRelationship.relationStatusForAffinity(19))
    }

    @Test
    fun `relationStatusForAffinity_相识档含20`() {
        // 20 是相识起点（下界含）
        assertEquals(RelationStatus.ACQUAINTANCE.name, ConversationRelationship.relationStatusForAffinity(20))
        assertEquals(RelationStatus.ACQUAINTANCE.name, ConversationRelationship.relationStatusForAffinity(39))
    }

    @Test
    fun `relationStatusForAffinity_友好档含40`() {
        assertEquals(RelationStatus.FRIENDLY.name, ConversationRelationship.relationStatusForAffinity(40))
        assertEquals(RelationStatus.FRIENDLY.name, ConversationRelationship.relationStatusForAffinity(69))
    }

    @Test
    fun `relationStatusForAffinity_亲密档含70及以上`() {
        assertEquals(RelationStatus.CLOSE.name, ConversationRelationship.relationStatusForAffinity(70))
        assertEquals(RelationStatus.CLOSE.name, ConversationRelationship.relationStatusForAffinity(200))
    }

    // ---- 数值 → 中文档位名（供互动中心/互动面板展示）----

    @Test
    fun `affinityTierLabel_档位中文名正确`() {
        assertEquals("陌生", ConversationRelationship.affinityTierLabel(0))
        assertEquals("相识", ConversationRelationship.affinityTierLabel(20))
        assertEquals("友好", ConversationRelationship.affinityTierLabel(40))
        assertEquals("亲密", ConversationRelationship.affinityTierLabel(70))
    }
}