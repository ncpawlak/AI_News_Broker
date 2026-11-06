package com.ainewsbroker.data

import com.ainewsbroker.model.Category
import com.ainewsbroker.model.SearchQuery

object DefaultQueries {
    const val CLAUDE = "claude"
    const val COPILOT = "copilot"
    const val PARADIGMS = "paradigms"

    val categories = listOf(
        Category(CLAUDE, "Claude"),
        Category(COPILOT, "Copilot"),
        Category(PARADIGMS, "Paradigms"),
    )

    val queries = listOf(
        SearchQuery(
            id = "claude-features",
            categoryId = CLAUDE,
            label = "Features & API",
            rawQuery = "\"Claude\" AND (\"Anthropic\" OR \"API\") AND (\"computer use\" OR \"artifacts\" OR \"tool use\" OR \"system prompt\" OR \"context caching\")",
        ),
        SearchQuery(
            id = "claude-prompting",
            categoryId = CLAUDE,
            label = "Prompt engineering",
            rawQuery = "\"Claude\" AND (\"prompt engineering\" OR \"metaprompt\" OR \"xml tags\" OR \"few-shot\" OR \"thinking budget\")",
        ),
        SearchQuery(
            id = "claude-cost",
            categoryId = CLAUDE,
            label = "Cost & performance",
            rawQuery = "\"Claude\" AND (\"pricing\" OR \"tokens per second\" OR \"haiku\" OR \"sonnet\" OR \"opus\") AND (\"benchmark\" OR \"latency\")",
        ),
        SearchQuery(
            id = "copilot-capabilities",
            categoryId = COPILOT,
            label = "Capabilities & extensions",
            rawQuery = "\"GitHub Copilot\" AND (\"workspace\" OR \"extensions\" OR \"agents\" OR \"chat participants\" OR \"custom instructions\")",
        ),
        SearchQuery(
            id = "copilot-governance",
            categoryId = COPILOT,
            label = "Governance & best practices",
            rawQuery = "\"GitHub Copilot\" AND (\"enterprise\" OR \"telemetry\" OR \"code quality\" OR \"security vulnerabilities\" OR \"licensing\")",
        ),
        SearchQuery(
            id = "paradigms-patterns",
            categoryId = PARADIGMS,
            label = "AI engineering patterns",
            rawQuery = "\"LLM architecture\" OR \"agentic workflow\" OR \"compound AI systems\" OR \"structured outputs\" OR \"LLM routing\"",
        ),
        SearchQuery(
            id = "paradigms-rag",
            categoryId = PARADIGMS,
            label = "RAG & retrieval",
            rawQuery = "(\"RAG\" OR \"retrieval augmented generation\") AND (\"graphRAG\" OR \"reranking\" OR \"hybrid search\" OR \"chunking strategy\")",
        ),
        SearchQuery(
            id = "paradigms-local",
            categoryId = PARADIGMS,
            label = "Local models & dev tools",
            rawQuery = "(\"Ollama\" OR \"vLLM\" OR \"Llama.cpp\") AND (\"local deployment\" OR \"inference optimization\" OR \"quantization\")",
        ),
    )
}
