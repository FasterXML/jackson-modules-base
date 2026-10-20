package tools.jackson.module.blackbird;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Immutable bean with a fluent builder, in the shape that Immutables and
 * Lombok generate.
 */
@JsonDeserialize(builder = Invoice.Builder.class)
public final class Invoice
{
    static final Invoice SAMPLE = new Builder()
            .number("INV-2026-000417")
            .customerId("cust-88412")
            .currency("EUR")
            .totalCents(1_250_075L)
            .taxCents(237_514L)
            .lineCount(14)
            .paid(true)
            .note("Net 30")
            .build();

    private final String number;
    private final String customerId;
    private final String currency;
    private final long totalCents;
    private final long taxCents;
    private final int lineCount;
    private final boolean paid;
    private final String note;

    private Invoice(Builder b) {
        number = b.number;
        customerId = b.customerId;
        currency = b.currency;
        totalCents = b.totalCents;
        taxCents = b.taxCents;
        lineCount = b.lineCount;
        paid = b.paid;
        note = b.note;
    }

    public String getNumber() { return number; }
    public String getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public long getTotalCents() { return totalCents; }
    public long getTaxCents() { return taxCents; }
    public int getLineCount() { return lineCount; }
    public boolean isPaid() { return paid; }
    public String getNote() { return note; }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder
    {
        private String number;
        private String customerId;
        private String currency;
        private long totalCents;
        private long taxCents;
        private int lineCount;
        private boolean paid;
        private String note;

        public Builder number(String v) { number = v; return this; }
        public Builder customerId(String v) { customerId = v; return this; }
        public Builder currency(String v) { currency = v; return this; }
        public Builder totalCents(long v) { totalCents = v; return this; }
        public Builder taxCents(long v) { taxCents = v; return this; }
        public Builder lineCount(int v) { lineCount = v; return this; }
        public Builder paid(boolean v) { paid = v; return this; }
        public Builder note(String v) { note = v; return this; }

        public Invoice build() {
            return new Invoice(this);
        }
    }
}
