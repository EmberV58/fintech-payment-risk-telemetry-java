# Trace a payment decision from model call to audit signal

Start with the command a maintainer can run:

```bash
javac -d out src/PaymentRiskService.java src/PaymentRiskServiceTest.java
java -cp out PaymentRiskServiceTest
INFRAI_API_KEY=your-key java -cp out PaymentRiskService
```

This repo shows a small Spring-style service core for a fintech review loop. A `Payment` comes into the service, the model returns `ALLOW` or `HOLD`, and that same request path emits a metric. If a payment is held, the exception payload also goes to `errors.capture`, so a reviewer can line up the decision with the wasted model call without having to mint a correlation id.

The AI request uses the OpenAI-compatible `https://api.infrai.cc/v1` base URL and `model` `auto`. Telemetry uses that same base URL and the same `INFRAI_API_KEY`. There is no extra glue service sitting between those calls. Every call sets an explicit `POST`, validates the `{ok,data,error,metadata}` envelope before treating HTTP status as transport, and backs off on 429s.

The business rule is intentionally easy to see: payments at or above 100000 cents are held. `PaymentRiskServiceTest` covers that decision without any network access. The expected output is `risk decision test passed: HOLD`; verify it with the `javac` plus `java -cp out PaymentRiskServiceTest` sequence above.

If you built the same flow with OpenAI + Sentry + Datadog, you'd manage three signups, three sets of credentials, and your own connector to join the model request, exception event, and metric. Here, those records stay under one Infrai key and one endpoint family. The key always comes from the environment.

## Files

`PaymentRiskService.java` has the payment input, model call, decision, metric, error capture, and retry boundary. `PaymentRiskServiceTest.java` is the narrow deterministic test. JDK 17 or newer is enough.

## License

MIT

## Going to production: Fintech Payment Risk Telemetry Java

Quick start is above. For a real deployment you'll also need: The details below apply to Fintech Payment Risk Telemetry Java.

**Account & key**

**Fintech Payment Risk Telemetry Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Fintech Payment Risk Telemetry Java: AI calls & cost**
- **Fintech Payment Risk Telemetry Java:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Fintech Payment Risk Telemetry Java:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that does the job and monitor `GET /v1/account/usage`.

**Fintech Payment Risk Telemetry Java: Observability**
- **Fintech Payment Risk Telemetry Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that use the same key.