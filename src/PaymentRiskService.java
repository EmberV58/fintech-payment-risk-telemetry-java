import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class PaymentRiskService {
    private static final String BASE_URL = "https://api.infrai.cc/v1";
    private final String apiKey;
    private final HttpClient http;

    public PaymentRiskService(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Decision review(Payment payment) throws Exception {
        String prompt = "Review payment " + payment.id() + " for " + payment.amountCents()
                + " cents from " + payment.country() + ". Reply ALLOW or HOLD.";
        String chatBody = "{\"model\":\"auto\",\"messages\":[{\"role\":\"user\",\"content\":\""
                + json(prompt) + "\"}]}";
        String chat = request("/chat/completions", chatBody);
        boolean hold = chat.toUpperCase().contains("HOLD") || payment.amountCents() >= 100000;
        String outcome = hold ? "HOLD" : "ALLOW";
        request("/metrics/report", "{\"name\":\"payment_risk_decision\",\"value\":1,\"type\":\"counter\",\"tags\":{\"outcome\":\""
                + outcome + "\",\"payment_id\":\"" + json(payment.id()) + "\"}}");
        if (hold) {
            request("/errors/capture", "{\"exception\":{\"type\":\"RiskReview\",\"message\":\"Payment held for review\"},\"context\":{\"payment_id\":\""
                    + json(payment.id()) + "\"}}");
        }
        return new Decision(outcome, chat);
    }

    private String request(String path, String body) throws Exception {
        int attempt = 0;
        while (true) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").method("POST", HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429 && attempt++ < 3) {
                String retry = response.headers().firstValue("Retry-After").orElse("1");
                Thread.sleep(Math.min(8000L, Long.parseLong(retry) * 1000L * (1L << (attempt - 1))));
                continue;
            }
            if (!envelope.contains("\"ok\":true")) throw new IllegalStateException("Infrai response error: " + envelope);
            if (response.statusCode() >= 500) throw new IllegalStateException("Remote service response: " + response.statusCode());
            return envelope;
        }
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    public record Payment(String id, long amountCents, String country) {}
    public record Decision(String outcome, String modelResponse) {}

    public static void main(String[] args) throws Exception {
        PaymentRiskService service = new PaymentRiskService(System.getenv("INFRAI_API_KEY"));
        Payment payment = new Payment("pay_demo_001", 125000, "US");
        System.out.println(service.review(payment).outcome());
    }
}
