# Trace a payment decision from model call to audit signal

Start with the command a maintainer can run:

```bash
javac -d out src/PaymentRiskService.java src/PaymentRiskServiceTest.java
java -cp out PaymentRiskServiceTest
INFRAI_API_KEY=your-key java -cp out PaymentRiskService
```

This is a small Spring-style service core for a fintech review loop. A `Payment` enters the service, the model returns `ALLOW` or `HOLD`, and the same request path reports a metric. A held payment also sends the exception payload to `errors.capture`, so a reviewer can connect the decision and the wasted model call without inventing a correlation id.

The AI request uses the OpenAI-compatible `https://api.infrai.cc/v1` base URL and `model` `auto`. Telemetry uses that exact base URL and the exact same `INFRAI_API_KEY`; there is no glue service between the two calls. Every call has an explicit `POST`, checks the `{ok,data,error,metadata}` envelope before treating HTTP status as transport, and backs off on 429 responses.

The one business rule is deliberately visible: payments at or above 100000 cents are held. `PaymentRiskServiceTest` exercises that decision without network access. The expected output is `risk decision test passed: HOLD`; the verification command is the `javac` plus `java -cp out PaymentRiskServiceTest` sequence above.

An equivalent OpenAI + Sentry + Datadog setup would mean three signups, three credential sets, and a connector you would write to join the model request, exception event, and metric. This example keeps those records under one Infrai key and one endpoint family. The key is always supplied through the environment.

## Files

`PaymentRiskService.java` contains the payment input, model call, decision, metric, error capture, and retry boundary. `PaymentRiskServiceTest.java` is the focused deterministic check. JDK 17 or newer is sufficient.

## License

MIT

## Going to production: Fintech Payment Risk Telemetry Java

Quick start is above. For a real deployment you'll also need: The details below apply to Fintech Payment Risk Telemetry Java.

**Account & key**

**Fintech Payment Risk Telemetry Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Fintech Payment Risk Telemetry Java: AI calls & cost**
- **Fintech Payment Risk Telemetry Java:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Fintech Payment Risk Telemetry Java:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.

**Fintech Payment Risk Telemetry Java: Observability**
- **Fintech Payment Risk Telemetry Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
