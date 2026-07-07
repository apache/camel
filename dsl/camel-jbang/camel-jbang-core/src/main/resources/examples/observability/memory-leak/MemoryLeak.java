import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class MemoryLeak extends RouteBuilder {

    // this collection grows forever — simulating a memory leak
    private final Map<String, byte[]> cache = new HashMap<>();

    @Override
    public void configure() {
        // leak 1: growing cache (lambda) — adds a 64 KB entry every 200ms, never evicts
        from("timer:cache-leak?period=200")
            .process(e -> {
                String key = "entry-" + cache.size();
                cache.put(key, new byte[65536]);
                e.getMessage().setBody("Cache size: " + cache.size()
                    + " (~" + (cache.size() * 64) + " KB)");
            })
            .to("log:cache-leak?level=INFO");

        // leak 2: growing buffer list (named class) — adds a 32 KB buffer every 300ms
        from("timer:buffer-leak?period=300")
            .process(new BufferLeakProcessor())
            .to("log:buffer-leak?level=INFO");

        // normal route for comparison — no leak
        from("timer:healthy?period=5000")
            .setBody(constant("I am healthy"))
            .to("log:healthy?level=INFO");
    }
}

class BufferLeakProcessor implements Processor {

    private final List<byte[]> buffers = new ArrayList<>();

    @Override
    public void process(Exchange exchange) {
        buffers.add(new byte[32768]);
        exchange.getMessage().setBody("Buffers: " + buffers.size()
            + " (~" + (buffers.size() * 32) + " KB)");
    }
}
