package org.apache.camel.component.undertow;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.support.RestConsumerContextPathMatcher;

public class UndertowConsumerResolver {

    public UndertowConsumer resolve(HttpServerExchange exchange, Map<String, UndertowConsumer> consumers) {
        UndertowConsumer answer = null;

        String path = exchange.getRequestPath();
        if (path == null) {
            return null;
        }
        HttpString method = exchange.getRequestMethod();
        if (method == null) {
            return null;
        }

        List<RestConsumerContextPathMatcher.ConsumerPath> paths = new ArrayList<RestConsumerContextPathMatcher.ConsumerPath>();
        for (final Map.Entry<String, UndertowConsumer> entry : consumers.entrySet()) {
            paths.add(new RestConsumerContextPathMatcher.ConsumerPath<UndertowConsumer>() {
                @Override
                public String getRestrictMethod() {
                    return entry.getValue().getEndpoint().getHttpMethodRestrict();
                }

                @Override
                public String getConsumerPath() {
                    return entry.getValue().getEndpoint().getHttpURI().getPath();
                }

                @Override
                public UndertowConsumer getConsumer() {
                    return entry.getValue();
                }
            });
        }

        RestConsumerContextPathMatcher.ConsumerPath<UndertowConsumer> best = RestConsumerContextPathMatcher.matchBestPath(method.toString(), path, paths);
        if (best != null) {
            answer = best.getConsumer();
        }

        if (answer == null) {
            for (String key : consumers.keySet()) {
                String consumerPath = consumers.get(key).getEndpoint().getHttpURI().getPath();
                UndertowConsumer consumer = consumers.get(key);
                boolean matchOnUriPrefix = consumer.getEndpoint().getMatchOnUriPrefix();
                if (RestConsumerContextPathMatcher.matchPath(path, consumerPath, matchOnUriPrefix)) {
                    answer = consumers.get(key);
                    break;
                }
            }
        }

        return answer;
    }
}

