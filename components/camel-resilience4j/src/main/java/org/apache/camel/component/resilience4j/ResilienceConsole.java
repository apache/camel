package org.apache.camel.component.resilience4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.console.AbstractDevConsole;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("resilience4j")
public class ResilienceConsole extends AbstractDevConsole {

    public ResilienceConsole() {
        super("camel", "resilience4j", "Resilience Circuit Breaker", "Display circuit breaker information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<ResilienceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof ResilienceProcessor) {
                    cbs.add((ResilienceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(ResilienceProcessor::getId));

        for (ResilienceProcessor cb : cbs) {
            String id = cb.getId();
            String rid = cb.getRouteId();
            String state = cb.getCircuitBreakerState();
            int sc = cb.getNumberOfSuccessfulCalls();
            int bc = cb.getNumberOfBufferedCalls();
            int fc = cb.getNumberOfFailedCalls();
            long npc = cb.getNumberOfNotPermittedCalls();
            float fr = cb.getFailureRate();
            if (fr > 0) {
                sb.append(String.format("    %s/%s: %s (buffered: %d success: %d failure: %d/%.0f%% not-permitted: %d)\n", rid,
                        id, state, bc, sc, fc, fr, npc));
            } else {
                sb.append(String.format("    %s/%s: %s (buffered: %d success: %d failure: 0 not-permitted: %d)\n", rid, id,
                        state, bc, sc, npc));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<ResilienceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof ResilienceProcessor) {
                    cbs.add((ResilienceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(ResilienceProcessor::getId));

        final List<JsonObject> list = new ArrayList<>();
        for (ResilienceProcessor cb : cbs) {
            JsonObject jo = new JsonObject();
            jo.put("id", cb.getId());
            jo.put("routeId", cb.getRouteId());
            jo.put("state", cb.getCircuitBreakerState());
            jo.put("bufferedCalls", cb.getNumberOfBufferedCalls());
            jo.put("successfulCalls", cb.getNumberOfSuccessfulCalls());
            jo.put("failedCalls", cb.getNumberOfFailedCalls());
            jo.put("notPermittedCalls", cb.getNumberOfNotPermittedCalls());
            jo.put("failureRate", cb.getFailureRate());
            list.add(jo);
        }
        root.put("circuitBreakers", list);

        return root;
    }
}
