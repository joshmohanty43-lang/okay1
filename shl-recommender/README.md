# SHL Assessment Recommender — Spring Boot

A conversational agent that recommends SHL Individual Test Solutions through dialogue, built with
Java 17 + Spring Boot 3. Implements the API contract from the assignment: `GET /health`,
`POST /chat` (stateless, full history in, `{reply, recommendations, end_of_conversation}` out).

## 1. Get it running

### 1a. Add the catalog
`src/main/resources/catalog/shl_catalog.json` ships with a ~90-item curated subset (tech skills,
cognitive ability, personality, simulations, biodata) so the app runs out of the box. **Replace
this file with your full scraped catalog** (the JSON array you already have from
`https://www.shl.com/solutions/products/product-catalog/`, Individual Test Solutions only) for
real coverage/Recall@10. The loader is schema-tolerant (`@JsonIgnoreProperties(ignoreUnknown =
true)`) and only requires `name` and `link`/`url` per record — everything else (duration, job
levels, keys/test-type, remote, adaptive, description) is optional and used when present.

### 1b. Configure an LLM
Any OpenAI-compatible `/chat/completions` endpoint works (Groq, OpenRouter, OpenAI). Set:

```bash
export LLM_API_BASE_URL=https://api.groq.com/openai/v1   # default shown
export LLM_API_KEY=sk-...
export LLM_MODEL=llama-3.3-70b-versatile
```

If `LLM_API_KEY` is unset, the service still boots and answers with a static clarifying question
instead of crashing (fail-safe, not fail-silent) — useful for smoke-testing `/health` and the
retrieval layer without a key.

### 1c. Run
```bash
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/shl-recommender-1.0.0.jar
```
Or with Docker: `docker build -t shl-recommender . && docker run -p 8080:8080 -e LLM_API_KEY=... shl-recommender`.

```bash
curl localhost:8080/health
curl -X POST localhost:8080/chat -H "Content-Type: application/json" -d '{
  "messages": [{"role":"user","content":"Hiring a Java developer who works with stakeholders"}]
}'
```

## 2. Design

**Grounding, not generation.** The LLM never writes an assessment name or URL into the response.
A single "planner" call (`PlannerService`) reads the full conversation and returns strict JSON
classifying the turn — `clarify | recommend | refine | compare | refuse` — plus a search query and
filters (test type, max duration, remote, job level). `SearchService` then ranks the *real*
catalog (manual TF-IDF over name+description+category, with hard filters and a fallback to the
unfiltered pool if filters zero everything out) and `ChatOrchestratorService` builds the
`recommendations` array directly from those catalog objects. This means `recommendations` can
never contain a hallucinated URL — worst case it's an imperfect ranking, never a fabricated entry.

**Statelessness → cumulative context, not memory.** Since the API is stateless, "refine" doesn't
special-case a stored shortlist. Every turn's planner call sees the *entire* message history and
is instructed to build its search query cumulatively ("add personality tests" + earlier "Java
developer" turns → one combined query), so refinement falls out of the same code path as the
initial recommendation instead of needing server-side session state.

**Compare is grounded with a second, narrower LLM call.** `compareNames` from the planner are
fuzzy-matched against the catalog first; only if 2+ real assessments resolve do we ask a second
LLM call to write the comparison, and that call's system prompt contains *only* the matched
assessments' real descriptions with an explicit "use only this data" instruction. If fewer than 2
names resolve, the agent asks for clarification instead of guessing.

**Turn cap.** The planner prompt is told which turn it's on; server-side, if it still returns
`clarify` on turn ≥7 of 8, the orchestrator forces `recommend` with whatever context exists rather
than violate the 8-turn cap.

**Refusal / scope.** Off-topic, general hiring/legal advice, and prompt-injection attempts are
classified by the planner as `refuse`; the system prompt explicitly tells the model to ignore any
in-message instructions that try to change its role or output format.

**Failure isolation.** Every LLM call is wrapped so a timeout/malformed response degrades to a
safe clarifying question rather than a 5xx or a schema-violating body (`GlobalExceptionHandler` +
try/catch in the controller give the same guarantee for unexpected exceptions).

## 3. What I'd improve with more time
- Swap the manual TF-IDF for a proper BM25 or embedding-based retriever once the full ~700-item
  catalog is in; TF-IDF is adequate at this scale but embeddings would help with paraphrased
  queries ("people-facing" → "stakeholder communication").
- Cache the planner's search-query embedding/tokens per conversation prefix to cut latency on
  refine turns.
- Add a small regex/keyword backstop for refusal (in addition to the LLM classifier) so refusal
  doesn't fully depend on one model call.

## 4. AI tool usage note
Drafted with AI-assisted authoring for boilerplate (POJOs, Spring wiring); the retrieval design,
grounding strategy, and prompt/response contract were reviewed and are understood in detail —
happy to walk through any part of it.
