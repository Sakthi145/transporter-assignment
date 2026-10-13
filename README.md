# Transporter Assignment on Lanes

A Spring Boot service that assigns transporters to freight lanes at **minimum total cost**, using **at most `maxTransporters`** distinct transporters, while covering **every lane**.

## Problem overview

- A **lane** is a route, e.g. Mumbai → Delhi.
- A **transporter** quotes a price for some lanes. It can serve a lane **only** if it has quoted that lane.
- Given a limit `K`, choose at most `K` transporters and assign each lane to one of them so that:
  1. every lane is covered,
  2. total cost is as low as possible,
  3. among options with the same cost, more transporters are used.

## Tech stack

Java 21 · Spring Boot 3.5 · Maven · Jakarta Bean Validation · springdoc-openapi (Swagger UI) · JUnit 5, Mockito, MockMvc, AssertJ. All data is stored in memory; there is no database.

## Running

Requires JDK 21 or newer. The Maven wrapper (`mvnw` / `mvnw.cmd`) downloads Maven, so you don't need Maven installed.

```bash
./mvnw clean test
```

```bash
./mvnw clean package
```

```bash
java -jar target/transporter-assignment.jar
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. The port comes from the `PORT` environment variable and defaults to `8080`.

### Docker

```bash
docker build -t transporter-assignment .
```

```bash
docker run -p 8080:8080 -e PORT=8080 transporter-assignment
```

The image is a multi-stage build (Maven + Temurin 21 to build, Temurin 21 JRE to run). It reads `PORT`, so it works on Render or Railway as is.

### API documentation

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Postman: import `postman/FreightFox_Transporter.postman_collection.json` and set `baseUrl` (default `http://localhost:8080`). Run it against a freshly started server so that the first request (no data yet, 409) behaves as expected.

## Endpoints

### `POST /api/v1/transporters/input`

Stores lanes and quotes. **Each call replaces the previously stored dataset.**

```json
{
  "lanes": [{"id": 1, "origin": "Mumbai", "destination": "Delhi"}],
  "transporters": [
    {"id": 1, "name": "Transporter T1", "laneQuotes": [{"laneId": 1, "quote": 5000}]}
  ]
}
```

Response `200`:

```json
{"status": "success", "message": "Input data saved successfully."}
```

Validation (all return `400`):

| Rule | Example message |
|---|---|
| `lanes` and `transporters` must be non-empty | `lanes: lanes must not be empty` |
| Lane `id` required; `origin` and `destination` not blank | `lanes[0].origin: origin must not be blank` |
| Transporter `id` and `name` required | `transporters[0].name: transporter name is required` |
| `quote` required and positive (whole number) | `transporters[0].laneQuotes[0].quote: quote must be positive` |
| No duplicate lane IDs | `Duplicate lane id: 1` |
| No duplicate transporter IDs | `Duplicate transporter id: 1` |
| Quotes must reference a known lane | `Transporter 1 quotes unknown lane id: 42` |
| A transporter may quote a lane only once | `Transporter 1 quotes lane 1 more than once` |
| Every lane must have at least one quote | `Lane 2 has no quotes` |

Bean-validation errors use the message `Validation failed`. Cross-field errors use `Invalid input data`. All violations found are listed together in `errors`.

### `POST /api/v1/transporters/assignment`

```json
{"maxTransporters": 3}
```

Response `200` (Test Case 1 from the assignment):

```json
{
  "status": "success",
  "totalCost": 60139,
  "assignments": [
    {"laneId": 1, "transporterId": 5, "cost": 11601},
    {"laneId": 2, "transporterId": 1, "cost": 10512},
    {"laneId": 3, "transporterId": 4, "cost": 14316},
    {"laneId": 4, "transporterId": 4, "cost": 10678},
    {"laneId": 5, "transporterId": 4, "cost": 13032}
  ],
  "selectedTransporters": [1, 4, 5]
}
```

- `assignments` is sorted by `laneId`. The extra `cost` field is the chosen transporter's quote, so the total can be checked.
- `selectedTransporters` lists the transporters **actually used**, sorted ascending.
- If `maxTransporters` is larger than the number of transporters, it is capped to that number.

Error responses:

```json
{"status": "error", "message": "Cannot cover all lanes with 2 transporters (minimum required: 3)", "timestamp": "2026-10-13T05:42:10.123Z"}
```

```json
{"status": "error", "message": "No input data submitted. Call /api/v1/transporters/input first.", "timestamp": "2026-10-13T05:42:10.123Z"}
```

## Error codes

Every error has the same shape: `{"status": "error", "message": "...", "timestamp": "...", "errors": [...]}`. The `errors` list is only included when there are field-level messages. Stack traces are never returned.

| HTTP | When |
|---|---|
| 400 | Bean validation failure, cross-field input violation, `maxTransporters` missing or < 1, malformed JSON (including fractional or non-numeric numbers) |
| 404 | Unknown path |
| 405 | Wrong HTTP method |
| 409 | Assignment requested before any input was submitted |
| 415 | Request body is not `application/json` |
| 422 | Lanes cannot be covered with `maxTransporters` transporters; the message includes the true minimum |
| 500 | Unexpected error (generic message; details are only logged) |

## Design

### Layers (package `com.freightfox`)

| Package | Responsibility |
|---|---|
| `controller` | Thin REST layer: `@Valid` request binding, OpenAPI annotations, delegates to services |
| `service` | `TransporterInputService` runs cross-field validation, maps DTOs to the model, and saves. `TransporterAssignmentService` loads the data, checks and caps `K`, calls the strategy, and maps the result to DTOs |
| `strategy` | `TransporterSelectionStrategy` interface and the `ExhaustiveSearchStrategy` solver |
| `repository` | `TransporterDataRepository` interface. `InMemoryTransporterDataRepository` holds an immutable snapshot in an `AtomicReference` |
| `model` | Immutable domain records (`Lane`, `Transporter`, `LaneQuote`, `TransporterDataset`, `AssignmentResult`, `LaneAssignment`) |
| `dto` | Request and response records with validation and schema annotations |
| `exception` | Custom exceptions and `GlobalExceptionHandler` |
| `config` | OpenAPI metadata |

### Strategy pattern

`TransporterAssignmentService` depends only on the `TransporterSelectionStrategy` interface. The exhaustive solver can be replaced (for example by an ILP solver such as OR-Tools, or a greedy or local-search heuristic) without changing the controller or the services.

### Exhaustive search

1. Precompute a dense `price[lane][transporter]` matrix, with lanes and transporters sorted by ID.
2. Enumerate every combination of 1..K transporters (iteratively, in lexicographic order).
3. For each combination, give every lane its cheapest quote within the combination. If two quotes are equal, pick the lowest transporter ID. Skip the combination if a lane has no quote.
4. Keep the best combination by: **lowest cost → more transporters actually used → lexicographically smallest list of used IDs**.
5. If nothing is feasible, continue the search for larger K to find the smallest K that covers every lane, and report it in the 422 message.

**Why it is exact:** every possible set of transporters of size ≤ K is evaluated. Once the set is fixed, giving each lane its cheapest quote is optimal, because lanes are independent.

**Why it is fast here, and its limit:** there are `C(n,1)+…+C(n,K)` combinations, each costing `O(L·K)`. With 7 transporters that is at most 127 combinations, which takes microseconds. The cost grows exponentially with the number of transporters: about 2^n combinations when K ≈ n. That is fine up to roughly 20–25 transporters. Beyond that, plug in an ILP or heuristic strategy.

Money is held as `long` throughout.

## Assumptions and notes

1. **Cost comes first.** "Maximize transporter usage up to the limit" only breaks ties between options of equal cost. A cheaper plan with fewer transporters always wins. For example, in Test Case 1 with K = 5, 6 or 7 only 4 transporters are used, because adding more cannot lower the cost.
2. **Only real quotes count.** A transporter can serve a lane only if it quoted that lane. A missing quote is never treated as zero or as some default price.
3. **Tie-break rules.** Within a lane, equal prices go to the lowest transporter ID. Between combinations: lowest total cost, then more distinct transporters actually used, then the lexicographically smallest sorted list of used IDs. Results are fully deterministic.
4. **Each input call replaces the previous dataset.** Data is not merged.
5. **Error codes.** 400 for invalid input, 409 when no data has been submitted, 422 when the limit is too small to cover all lanes.
6. **The sample outputs in the assignment PDF are inconsistent, so this implementation follows the written rules, not the sample numbers:**
   - The 10-lane API example claims a K = 3 result with total 29,000 using transporters [1, 2, 3]. But lanes 7–10 are quoted only by T4 and T5, and each pair of lanes {1,2}, {3,4}, {5,6}, {7,8}, {9,10} has its own disjoint set of transporters. So **at least 5 transporters are required**. The optimum at K = 5 is 48,000 with [1, 2, 3, 4, 5], and K = 3 returns 422.
   - Test Case 1's sample assigns lane 3 to T2, but T2 is not in its `selectedTransporters` [4, 6, 3]. Its total of 75,000 also doesn't match the listed assignments, which sum to 69,001. The true optimum for K = 3 is **60,139** with [1, 4, 5].
7. Quotes and IDs must be whole numbers. Fractional values are rejected as malformed JSON rather than silently truncated.

## Verified results

| Dataset | K | Total cost | Selected |
|---|---|---|---|
| 9-lane Excel data | 1 / 2 / 3 / 4 / 5–7 | 201,259 / 148,402 / 134,876 / 127,844 / 125,045 | [1] / [1,4] / [1,4,6] / [1,3,4,7] / [1,3,4,5,7] |
| Test Case 1 (5 lanes) | 1 / 2 / 3 / ≥4 | 96,940 / 62,938 / 60,139 / 59,896 | [4] / [1,4] / [1,4,5] / [1,3,4,5] |
| Test Case 2 | 1–2 / 3 / 4 | 422 (min 3) / 7,500 / 7,450 | — / [1,2,3] / [1,2,3,4] |
| 10-lane API example | 3–4 / 5 | 422 (min 5) / 48,000 | — / [1,2,3,4,5] |

All of these are covered by `ExhaustiveSearchStrategyTest`.
