package camel.example;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hands out the shop's order numbers, ORD-1001, ORD-1002, ... A plain Java class next to the route, declared as a
 * bean in beans.yaml and called from the route with a method expression.
 */
public class OrderNumber {

    private final AtomicInteger counter = new AtomicInteger();

    public void setStart(int start) {
        counter.set(start - 1);
    }

    public String next() {
        return "ORD-" + counter.incrementAndGet();
    }
}
