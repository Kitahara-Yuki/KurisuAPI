---
name: "multi-search-engine"
description: "Multi-engine web search aggregator supporting 17 search engines (Google, Baidu, Bing, DuckDuckGo, Brave, WolframAlpha, etc.) with zero configuration and no API keys required."
---

---
name: multi-search-engine
description: "Multi-engine web search aggregator supporting 17 search engines (Google, Baidu, Bing, DuckDuckGo, Brave, WolframAlpha, etc.) with zero configuration and no API keys required. Use when: (1) User wants to search the web for information, (2) User needs results from multiple search engines, (3) User wants to compare search results across sources, (4) User needs specialized search (academic, code, news)."
---

# Multi-Search-Engine Skill

Aggregate search results from 17 search engines (8 domestic + 9 international) for comprehensive information retrieval.

## Supported Search Engines

| Category | Engines |
|----------|---------|
| 国内搜索引擎 | Baidu, Bing中国, 360搜索, Sogou, 微信搜一搜(Sogou), 头条搜索, 集思录, etc. |
| 国际搜索引擎 | Google, Google HK, DuckDuckGo, Yahoo, Startpage, Brave, Ecosia, Qwant, WolframAlpha |

## Usage Guidelines

### General Search
Use `web_fetch` or `WebSearch` tool to search across engines. Choose engine based on needs:

| Need | Recommended Engine |
|------|-------------------|
| General web search | Google or Bing |
| Chinese content | Baidu or Sogou |
| Privacy-focused | DuckDuckGo or Startpage |
| Knowledge/computation | WolframAlpha |
| Open source/code | Google (with site:github.com) |

### Search Patterns

**Basic search:**
```
搜索 "关键词" 使用 [引擎名]
```

**Site-specific search:**
```
搜索 site:github.com react components
```

**File-type filtering:**
```
搜索 machine learning filetype:pdf
```

**Multi-engine comparison:**
When user needs comprehensive results, search across 2-3 engines and synthesize results.

## Workflow

1. **Understand** the user's search intent and preferred language
2. **Select** appropriate search engine(s)
3. **Execute** search using web tools
4. **Synthesize** results from multiple sources
5. **Present** findings with citations

## Best Practices

1. **Verify critical claims** across multiple sources
2. **Prefer authoritative sources** (official docs, academic papers, reputable media)
3. **Respect rate limits** — don't spam search engines
4. **Consider language** — use appropriate engines for Chinese vs English content
5. **Cite sources** — always provide URLs for retrieved information

