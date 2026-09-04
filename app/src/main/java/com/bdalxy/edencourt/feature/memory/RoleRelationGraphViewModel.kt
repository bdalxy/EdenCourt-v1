package com.bdalxy.edencourt.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * B4 角色关系图谱节点：一个会话角色（含开关态 + 与「我」的亲密度）。
 * @param graphEnabled 是否为图谱成员（Conversation.graphEnabled）
 * @param affinityWithMe 该会话角色与「我」的亲密度（会话 affinityScore，A1）
 * @param relationWithMe 中文档位（陌生/相识/友好/亲密/疏远，由亲密度分档）
 */
data class RoleRelationNode(
    val convId: Long,
    val cardId: Long?,
    val displayName: String,
    val avatar: String?,
    val graphEnabled: Boolean,
    val affinityWithMe: Int,
    val relationWithMe: String,
)

/** B4 选中角色后的关系详情：该角色 ↔ 我 + 该角色 ↔ 其它开启图谱角色 */
data class RoleRelationDetail(
    /** 该角色与「我」：亲密度 + 档位 */
    val meAffinity: Int,
    val meRelation: String,
    /** 该角色与其它开启图谱角色（已是图谱成员的会话，除自己外）的关系列表 */
    val others: List<Pair<String, String>>, // (对方名字, 中文档位)
)

/**
 * B4 关系图谱 ViewModel（用户规格：`我` 恒开启置顶独占行，开启图谱的会话角色两两成行，
 * 点某角色展开其与「我」及与其它开启角色的关系）。
 */
class RoleRelationGraphViewModel(
    private val conversationDao: ConversationDao,
    private val characterCardDao: CharacterCardDao,
    private val relationshipDao: RelationshipDao,
    private val userIdentityDao: UserIdentityDao,
) : ViewModel() {

    /** 用户身份名（未设置时为「我」） */
    private val _meName = MutableStateFlow("我")
    val meName: StateFlow<String> = _meName.asStateFlow()

    /** 全部会话角色节点（含开启与否，供图谱展示 + 编辑成员开关） */
    private val _nodes = MutableStateFlow<List<RoleRelationNode>>(emptyList())
    val nodes: StateFlow<List<RoleRelationNode>> = _nodes.asStateFlow()

    /** 仅开启图谱的节点（图谱主体；按亲密度降序，越熟越靠前） */
    val enabledNodes: StateFlow<List<RoleRelationNode>> =
        _nodes.map { it.filter { n -> n.graphEnabled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前点选的角色会话 id（null=未选） */
    private val _selectedConvId = MutableStateFlow<Long?>(null)
    val selectedConvId: StateFlow<Long?> = _selectedConvId.asStateFlow()

    /** 选中角色的关系详情（null=未选或无数据源） */
    private val _detail = MutableStateFlow<RoleRelationDetail?>(null)
    val detail: StateFlow<RoleRelationDetail?> = _detail.asStateFlow()

    init {
        // 「我」身份名（用户自设身份，T8.13）
        viewModelScope.launch {
            _meName.value = userIdentityDao.get()?.name?.takeIf { it.isNotBlank() } ?: "我"
        }
        // 聚合会话 + 角色卡（卡名/头像），派生节点
        combine(conversationDao.observeAll(), characterCardDao.observeAll()) { convs, cards ->
            val byId = cards.associateBy { it.id }
            convs.map { c -> toNode(c, byId[c.characterCardId]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            .also { flow ->
                viewModelScope.launch {
                    flow.collect { list ->
                        _nodes.value = list
                        refreshDetail(list)
                    }
                }
            }
    }

    /** 会话 → 图谱节点（角色名取 备注>卡名>会话标题；无卡用占位） */
    private fun toNode(c: Conversation, card: CharacterCardEntity?): RoleRelationNode {
        val name = card?.remark?.takeIf { it.isNotBlank() }
            ?: card?.name?.takeIf { it.isNotBlank() }
            ?: c.title.takeIf { it.isNotBlank() } ?: "未命名角色"
        return RoleRelationNode(
            convId = c.id,
            cardId = c.characterCardId,
            displayName = name,
            avatar = card?.avatar,
            graphEnabled = c.graphEnabled,
            affinityWithMe = c.affinityScore,
            relationWithMe = ConversationRelationship.affinityTierLabel(c.affinityScore),
        )
    }

    /** 选中角色，并立即刷新其关系详情 */
    fun select(convId: Long?) {
        _selectedConvId.value = convId
        refreshDetail(_nodes.value)
    }

    /** 切换某会话的图谱开关（B4 会话级） */
    fun toggleGraph(convId: Long) {
        viewModelScope.launch {
            val conv = conversationDao.getById(convId) ?: return@launch
            conversationDao.update(conv.copy(graphEnabled = !conv.graphEnabled))
        }
    }

    /** 重算选中角色的关系详情（该角色↔我 + 该角色↔其它开启图谱角色） */
    private fun refreshDetail(nodes: List<RoleRelationNode>) {
        val sel = _selectedConvId.value ?: return
        val me = nodes.firstOrNull { it.convId == sel } ?: return
        viewModelScope.launch {
            val others = nodes
                .filter { it.graphEnabled && it.convId != sel }
                .mapNotNull { other ->
                    val rel = relationshipDao.getBetween(sel, other.convId)
                    val tier = if (rel != null) {
                        ConversationRelationship.affinityTierLabel(rel.affinityScore)
                    } else {
                        "尚未相交" // 无 conv_relationship：两角色还没正式互动过
                    }
                    other.displayName to tier
                }
            _detail.value = RoleRelationDetail(
                meAffinity = me.affinityWithMe,
                meRelation = me.relationWithMe,
                others = others,
            )
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                RoleRelationGraphViewModel(
                    conversationDao = app.conversationDao,
                    characterCardDao = app.characterCardDao,
                    relationshipDao = app.relationshipDao,
                    userIdentityDao = app.userIdentityDao,
                )
            }
        }
    }
}