package com.ainewsbroker.data

import com.ainewsbroker.model.Category
import com.ainewsbroker.model.QuerySource
import com.ainewsbroker.model.SearchQuery

/**
 * Built-in searches, tuned for "what's new": official changelogs for exact product updates,
 * Bing News for coverage, and Hacker News for new tools that news sites haven't picked up yet.
 * Bing works best with short phrases; long boolean chains drift off-topic.
 */
object DefaultQueries {
    const val CLAUDE = "claude"
    const val COPILOT = "copilot"
    const val WORKFLOWS = "workflows"
    const val TOOLS = "tools"

    /** Bump when [queries] changes so existing installs pick up the new defaults. */
    const val VERSION = 3

    val categories = listOf(
        Category(CLAUDE, "Claude"),
        Category(COPILOT, "Copilot"),
        Category(WORKFLOWS, "Workflows"),
        Category(TOOLS, "New tools"),
    )

    val queries = listOf(
        // Claude
        SearchQuery(
            id = "claude-code-releases",
            categoryId = CLAUDE,
            label = "Claude Code releases",
            rawQuery = "https://github.com/anthropics/claude-code/releases.atom",
            source = QuerySource.Feed,
            titlePrefix = "Claude Code",
        ),
        SearchQuery(
            id = "claude-product-news",
            categoryId = CLAUDE,
            label = "Product news",
            rawQuery = "Anthropic Claude new feature",
        ),
        SearchQuery(
            id = "claude-code-news",
            categoryId = CLAUDE,
            label = "Claude Code",
            rawQuery = "\"Claude Code\"",
        ),
        // Copilot
        SearchQuery(
            id = "copilot-changelog",
            categoryId = COPILOT,
            label = "GitHub changelog",
            rawQuery = "https://github.blog/changelog/label/copilot/feed/",
            source = QuerySource.Feed,
        ),
        SearchQuery(
            id = "copilot-news",
            categoryId = COPILOT,
            label = "Copilot news",
            rawQuery = "\"GitHub Copilot\" AND (\"agent\" OR \"Copilot CLI\" OR \"coding agent\" OR \"new model\" OR \"public preview\" OR \"generally available\" OR \"launches\")",
        ),
        SearchQuery(
            id = "copilot-tips",
            categoryId = COPILOT,
            label = "Tips & features",
            rawQuery = "\"GitHub Copilot\" (tips OR \"custom instructions\" OR \"best practices\" OR workflow)",
        ),
        // Workflows: using agents better (parallel/fleet usage, context, optimization)
        SearchQuery(
            id = "workflows-coding-agents",
            categoryId = WORKFLOWS,
            label = "Coding agent workflows",
            rawQuery = "\"coding agents\" workflow",
        ),
        SearchQuery(
            id = "workflows-agentic-coding",
            categoryId = WORKFLOWS,
            label = "Agentic coding",
            rawQuery = "\"agentic coding\" OR \"context engineering\"",
        ),
        SearchQuery(
            id = "workflows-hn",
            categoryId = WORKFLOWS,
            label = "Hacker News discussions",
            rawQuery = "coding agent OR agentic OR multi-agent OR Claude Code OR subagents OR worktrees",
            source = QuerySource.HackerNews,
            minPoints = 50,
        ),
        // New tools (e.g. agent orchestrators like Paseo)
        SearchQuery(
            id = "tools-open-source-agents",
            categoryId = TOOLS,
            label = "Open-source agents",
            rawQuery = "\"open-source\" \"coding agent\"",
        ),
        SearchQuery(
            id = "tools-launches",
            categoryId = TOOLS,
            label = "New dev tools",
            rawQuery = "\"AI coding agent\" launches",
        ),
        SearchQuery(
            id = "tools-show-hn",
            categoryId = TOOLS,
            label = "Show HN launches",
            rawQuery = "agent OR Claude OR Copilot OR Codex OR MCP OR LLM",
            source = QuerySource.ShowHN,
            minPoints = 5,
        ),
    )
}
