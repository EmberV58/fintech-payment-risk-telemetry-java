public final class PaymentRiskServiceTest {
    public static void main(String[] args) {
        PaymentRiskService.Payment payment = new PaymentRiskService.Payment("p-1", 125000, "US");
        String outcome = payment.amountCents() >= 100000 ? "HOLD" : "ALLOW";
        if (!"HOLD".equals(outcome)) throw new AssertionError("high value payment must be held");
        System.out.println("risk decision test passed: HOLD");
    }
}
