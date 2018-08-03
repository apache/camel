package org.apache.camel.graalvm.example;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.graalvm.CamelRuntime;
import org.apache.camel.graalvm.Reflection;
import org.apache.camel.impl.DefaultExchange;

public class SimpleCamelRouteBuilder extends CamelRuntime {

    public static void main(String[] args) throws Exception {
        new SimpleCamelRouteBuilder().run(args);
    }

    @Override
    public void configure() {

        bind("orderService", new MyOrderService());

        from("file:./target/orders?idempotent=true")
            .setHeader("orderState", MyOrderState::new)
            .split(body().tokenize("@"), SimpleCamelRouteBuilder.this::aggregate)
            // each splitted message is then send to this bean where we can process it
            .bean("orderService", "handleOrder(${header.orderState}, ${body})")
            // this is important to end the splitter route as we do not want to do more routing
            // on each splitted message
            .end()
            // after we have splitted and handled each message we want to send a single combined
            // response back to the original caller, so we let this bean build it for us
            // this bean will receive the result of the aggregate strategy: MyOrderStrategy
            .bean("orderService", "buildCombinedResponse(${header.orderState}, ${body})")
            // log out
            .to("log:out");
    }

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // put order together in old exchange by adding the order from new exchange
        List<String> orders;
        if (oldExchange != null) {
            orders = (List) oldExchange.getIn().getBody();
        } else {
            orders = new ArrayList<>();
            oldExchange = new DefaultExchange(newExchange.getContext());
            oldExchange.getIn().copyFromWithNewBody(newExchange.getIn(), orders);
        }
        String newLine = newExchange.getIn().getBody(String.class);

        log.debug("Aggregate old orders: " + orders);
        log.debug("Aggregate new order: " + newLine);

        // add orders to the list
        orders.add(newLine);

        // return old as this is the one that has all the orders gathered until now
        return oldExchange;
    }

    public class MyOrderService {

        @Reflection
        public String handleOrder(MyOrderState state, String line) {
            return state.handleOrder(line);
        }

        @Reflection
        public Map<String, Object> buildCombinedResponse(MyOrderState state, List<String> lines) {
            return state.buildCombinedResponse(lines);
        }

    }

    public class MyOrderState {

        private int counter;

        /**
         * We just handle the order by returning a id line for the order
         */
        public synchronized String handleOrder(String line) {
            log.debug("HandleOrder: " + line);
            return "(id=" + ++counter + ",item=" + line + ")";
        }

        /**
         * We use the same bean for building the combined response to send
         * back to the original caller
         */
        public synchronized Map<String, Object> buildCombinedResponse(List<String> lines) {
            log.debug("BuildCombinedResponse: " + lines);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("lines", lines);
            return result;
        }
    }

}
