---
name: "summarize"
description: "Universal content summarizer — summarize web pages, PDFs, YouTube videos, audio/podcasts, images, and local files into structured key points."
---

---
name: summarize
description: "Universal content summarizer — summarize web pages, PDFs, YouTube videos, audio/podcasts, images, and local files into structured key points. Use when: (1) User shares a URL and asks for a summary, (2) User uploads a PDF or document and wants key takeaways, (3) User wants to quickly consume a YouTube video or podcast, (4) User needs to extract key points from long articles, (5) User wants to understand code or log files."
---

# Summarize Skill

Universal content summarizer — point at any URL, video, PDF, audio, or file and get the gist.

## Supported Input Types

| Input Type | How It Works |
|------------|-------------|
| Web pages (URLs) | Auto-fetches page content, strips clutter, summarizes |
| PDF files | Reads local/remote PDFs, extracts text, summarizes |
| YouTube videos | Extracts transcripts/subtitles, summarizes with timestamps |
| Audio / Podcasts | Transcribes audio then summarizes |
| Images | OCR then summarizes visual content |
| Local files | Direct summarization of .txt, .md, code files |
| Video files | Frame/slide extraction + OCR + transcript |

## Usage Patterns

### Web Page Summary
When user shares a URL:
1. **Fetch** the page content using `web_fetch`
2. **Read** the content
3. **Extract** main points, key arguments, conclusions
4. **Present** as a structured summary

### Video/Audio Summary
When user shares a video or audio link:
1. **Check** for available transcripts/subtitles via API
2. **Extract** the transcript content
3. **Identify** key sections and timestamps
4. **Summarize** with structured format

### PDF/Document Summary
When user uploads or links a PDF:
1. **Read** the document content
2. **Extract** key sections (abstract, findings, conclusions)
3. **Summarize** with key takeaways
4. **Note** page references for important claims

### File/Code Summary
When user wants code or log file understanding:
1. **Read** the file content
2. **Identify** structure, patterns, and key components
3. **Summarize** the purpose and important sections
4. **Highlight** potential issues or notable patterns

## Output Formats

Adapt summary length to user needs:

| Format | When to Use |
|--------|-------------|
| **Short (2-3 bullets)** | Quick overview, casual request |
| **Medium (paragraph + bullets)** | Default — balanced depth |
| **Long (structured with sections)** | Research, detailed analysis |
| **Key points only** | Action-oriented, decision support |
| **Bullet list** | Easy scanning, comparison |

## Summary Structure

For comprehensive summaries, include these sections where applicable:

1. **Core Topic** — what is this about in one sentence
2. **Key Points** — 3-7 main takeaways
3. **Supporting Details** — evidence, examples, data
4. **Conclusion / Implications** — what this means
5. **Notable Quotes or Stats** — if relevant

## Best Practices

1. **Always cite sources** — mention where specific information comes from
2. **Be faithful** — don't invent details not in the source
3. **Adapt to medium** — video summaries should include timestamps; PDF summaries should reference sections
4. **Flag uncertainty** — if content is paywalled or inaccessible, say so
5. **Highlight action items** — if the content contains tasks or decisions

## Limitations

- Cannot access paywalled or login-protected content
- Video without captions/transcripts cannot be summarized without audio processing
- Very long content may be truncated by context limits
- OCR accuracy varies with image quality

