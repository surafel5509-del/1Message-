package com.onemessages.app.data.repository

import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.CategoryRuleDao
import com.onemessages.app.data.local.CategoryRuleEntity
import com.onemessages.app.data.local.DraftDao
import com.onemessages.app.domain.SmartOrganizer
import com.onemessages.app.model.CategoryRule
import com.onemessages.app.model.ConversationCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns user-editable categorisation rules. Built-in rules are seeded once and can be
 * disabled or deleted by the user, which keeps automation transparent and reversible.
 */
@Singleton
class OrganizationRepository @Inject constructor(
    private val ruleDao: CategoryRuleDao,
    private val draftDao: DraftDao,
) {

    val rules: Flow<List<CategoryRule>> = ruleDao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        runCatching {
            if (ruleDao.count() == 0) {
                ruleDao.insertAll(SmartOrganizer.builtInRules.map { it.toEntity() })
                Logx.i("Rules", "seeded built-in category rules")
            }
        }.onFailure { Logx.w("Rules", "seeding rules failed", it) }
    }

    suspend fun rulesSnapshot(): List<CategoryRule> = withContext(Dispatchers.IO) {
        ensureSeeded()
        runCatching { ruleDao.enabledRules().map { it.toModel() } }
            .getOrElse { SmartOrganizer.builtInRules }
    }

    suspend fun addRule(keyword: String, category: ConversationCategory): Boolean =
        withContext(Dispatchers.IO) {
            val clean = keyword.trim().take(48)
            if (clean.length < 2) return@withContext false
            runCatching {
                ruleDao.upsert(
                    CategoryRuleEntity(keyword = clean, category = category.name)
                )
                true
            }.getOrElse {
                Logx.w("Rules", "add rule failed", it)
                false
            }
        }

    suspend fun setRuleEnabled(rule: CategoryRule, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            ruleDao.upsert(rule.copy(enabled = enabled).toEntity())
        }

    suspend fun deleteRule(rule: CategoryRule) = withContext(Dispatchers.IO) {
        if (rule.isBuiltIn) {
            // Built-ins are disabled rather than removed so defaults can be restored.
            ruleDao.upsert(rule.copy(enabled = false).toEntity())
        } else {
            ruleDao.deleteUserRule(rule.id)
        }
    }

    suspend fun clearDrafts() = withContext(Dispatchers.IO) { draftDao.clear() }

    private fun CategoryRuleEntity.toModel() = CategoryRule(
        id = id,
        keyword = keyword,
        category = ConversationCategory.fromStorage(category) ?: ConversationCategory.PERSONAL,
        enabled = enabled,
        isBuiltIn = isBuiltIn,
    )

    private fun CategoryRule.toEntity() = CategoryRuleEntity(
        id = id,
        keyword = keyword,
        category = category.name,
        enabled = enabled,
        isBuiltIn = isBuiltIn,
    )
}
