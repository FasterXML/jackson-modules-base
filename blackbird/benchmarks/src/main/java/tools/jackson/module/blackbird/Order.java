package tools.jackson.module.blackbird;

import java.util.List;

/**
 * Record graph: nested records and a list of records.
 */
public record Order(String id, long amountCents, boolean expedited, Customer customer, List<Line> lines)
{
    public record Customer(String name, String email, int tier) { }

    public record Line(String sku, int quantity, long priceCents) { }

    static final Order SAMPLE = new Order("ord-20261014-0042", 1_234_599L, true,
            new Customer("Ada Lovelace", "ada@example.com", 3),
            List.of(new Line("sku-1001", 2, 19_999L),
                    new Line("sku-2002", 1, 1_049_900L),
                    new Line("sku-3003", 12, 5_475L)));
}
