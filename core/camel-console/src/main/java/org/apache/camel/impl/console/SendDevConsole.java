/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl.console;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "send", displayName = "Camel Send", description = "Send messages to endpoints", readOnly = false)
@Configurer(extended = true)
public class SendDevConsole extends AbstractDevConsole {

    public record Response(
            @Metadata(description = "Epoch time in milliseconds when the send was performed") long timestamp,
            @Metadata(description = "The send status, success/error/timeout") String status,
            @Metadata(description = "Elapsed time in milliseconds") long elapsed,
            @Metadata(description = "The endpoint URI (only present when known)") String endpoint,
            @Metadata(description = "The exception, as an opaque JSON object (only present on error)") Map<String, Object> exception,
            @Metadata(description = "The exchange ID (only present when a response message is available)") String exchangeId,
            @Metadata(description = "The response message, as an opaque JSON object (only present when a response message is available)") Map<String, Object> message) {
    }

    private ProducerTemplate producer;
    private ConsumerTemplate consumer;

    @Metadata(defaultValue = "32768",
              description = "Maximum size of the message body to include in the dump")
    private int bodyMaxChars = 32 * 1024;

    @Metadata(defaultValue = "20000", label = "advanced",
              description = "Timeout when using poll mode")
    private int pollTimeout = 20000;

    @Metadata(label = "query", description = "Maximum size of the message body to include in the dump",
              javaType = "java.lang.Integer", defaultValue = "32768")
    public static final String BODY_MAX_CHARS = "bodyMaxChars";

    @Metadata(label = "query", description = "The message body to send. Can refer to files using file: prefix",
              javaType = "java.lang.String")
    public static final String BODY = "body";

    @Metadata(label = "query", description = "Whether to poll message from the endpoint instead of sending",
              javaType = "java.lang.Boolean")
    public static final String POLL = "poll";

    @Metadata(label = "query", description = "Timeout when using poll mode",
              javaType = "java.lang.Integer", defaultValue = "20000")
    public static final String POLL_TIMEOUT = "pollTimeout";

    @Metadata(label = "query", description = "Exchange pattern when sending",
              javaType = "java.lang.String", enums = "InOnly,InOut")
    public static final String EXCHANGE_PATTERN = "exchangePattern";

    @Metadata(label = "query",
              description = "Endpoint for where to send messages (can also refer to a route id, endpoint pattern)",
              javaType = "java.lang.String")
    public static final String ENDPOINT = "endpoint";

    public SendDevConsole() {
        super("camel", "send", "Camel Send", "Send messages to endpoints");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        consumer = getCamelContext().createConsumerTemplate();
        producer = getCamelContext().createProducerTemplate();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(consumer, producer);
    }

    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    public void setBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
    }

    public int getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(int pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        StopWatch watch = new StopWatch();
        String endpoint = optionString(options, ENDPOINT);
        String body = optionString(options, BODY);
        if (body == null) {
            body = "";
        }
        String exchangePattern = optionString(options, EXCHANGE_PATTERN);
        boolean poll = optionBoolean(options, POLL, false);
        int timeout = optionInt(options, POLL_TIMEOUT, pollTimeout);
        // give extra time as CLI needs to process reply also
        timeout += 5000;

        Endpoint target = null;
        Exchange out = null;
        Exception cause = null;
        try {
            target = findTarget(endpoint);
            out = findToTarget(target, poll, timeout, exchangePattern, body, options);
        } catch (Exception e) {
            cause = e;
        }
        if (endpoint != null && target == null) {
            cause = new NoSuchEndpointException(endpoint);
        }
        if (out != null && out.getException() != null) {
            cause = out.getException();
        }
        long taken = watch.taken();
        String status = "success";
        if (cause != null) {
            status = "error";
        } else if (poll && out == null) {
            status = "timeout";
        }

        if (target != null) {
            sb.append(String.format("%n    Endpoint: %s", target));
        } else if (endpoint != null) {
            sb.append(String.format("%n    Endpoint: %s", endpoint));
        }
        sb.append(String.format("%n    Status: %s", status));
        sb.append(String.format("%n    Elapsed: %s", TimeUtils.printDuration(taken)));
        if (cause != null) {
            sb.append(String.format("%n    Error Message: %s", cause.getMessage()));
            final String stackTrace = ExceptionHelper.stackTraceToString(cause);
            sb.append("\n\n");
            sb.append(stackTrace);
            sb.append("\n\n");
        }
        if (out != null && (poll || "InOut".equals(exchangePattern))) {
            sb.append("\n    Response Message:\n\n");
            int maxChars = optionInt(options, BODY_MAX_CHARS, bodyMaxChars);
            String json
                    = MessageHelper.dumpAsJSon(out.getMessage(), true, true, true, 2, true, true, true,
                            maxChars, true);
            sb.append(json);
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        StopWatch watch = new StopWatch();
        long timestamp = System.currentTimeMillis();
        String endpoint = optionString(options, ENDPOINT);
        String body = optionString(options, BODY);
        if (body == null) {
            body = "";
        }
        String exchangePattern = optionString(options, EXCHANGE_PATTERN);
        boolean poll = optionBoolean(options, POLL, false);
        int timeout = optionInt(options, POLL_TIMEOUT, pollTimeout);
        // give extra time as CLI needs to process reply also
        timeout += 5000;

        Endpoint target = null;
        Exchange out = null;
        Exception cause = null;
        try {
            target = findTarget(endpoint);
            out = findToTarget(target, poll, timeout, exchangePattern, body, options);
        } catch (Exception e) {
            cause = e;
        }
        if (cause == null && endpoint != null && target == null) {
            cause = new NoSuchEndpointException(endpoint);
        }
        if (out != null && out.getException() != null) {
            cause = out.getException();
        }
        long taken = watch.taken();
        String status = "success";
        if (cause != null) {
            status = "error";
        } else if (poll && out == null) {
            status = "timeout";
        }

        String endpointStr = null;
        if (target != null) {
            endpointStr = target.toString();
        } else if (endpoint != null) {
            endpointStr = endpoint;
        }

        Map<String, Object> exception = null;
        if (cause != null) {
            // avoid double wrap
            exception = MessageHelper.dumpExceptionAsJSonObject(cause).getMap("exception");
        }

        String exchangeId = null;
        Map<String, Object> message = null;
        if (out != null && (poll || "InOut".equals(exchangePattern))) {
            exchangeId = out.getExchangeId();
            int maxChars = optionInt(options, BODY_MAX_CHARS, bodyMaxChars);
            // avoid double wrap
            message = MessageHelper.dumpAsJSonObject(out.getMessage(), true, true, true, true, true, true, maxChars)
                    .getMap("message");
        }

        Response response = new Response(timestamp, status, taken, endpointStr, exception, exchangeId, message);
        return JsonRecordSupport.toJsonObject(response);
    }

    private Exchange findToTarget(
            Endpoint target, boolean poll, int timeout, String exchangePattern, String body, Map<String, Object> options)
            throws Exception {
        Exchange out = null;
        if (target != null) {
            final Object inputBody = prepareBody(body);
            final Map<String, Object> inputHeaders = prepareHeaders(options);
            if (poll) {
                out = consumer.receive(target, timeout);
            } else {
                final String mep = exchangePattern;
                out = producer.send(target, exchange -> {
                    exchange.getMessage().setBody(inputBody);
                    if (!inputHeaders.isEmpty()) {
                        exchange.getMessage().setHeaders(inputHeaders);
                    }
                    exchange.setPattern(
                            "InOut".equals(mep) ? ExchangePattern.InOut : ExchangePattern.InOnly);
                });
            }
            if (inputBody instanceof Closeable c) {
                IOHelper.close(c);
            }
        }
        return out;
    }

    private Endpoint findTarget(String endpoint) {
        Endpoint target = null;
        if (endpoint == null) {
            List<Route> routes = getCamelContext().getRoutes();
            if (!routes.isEmpty()) {
                // grab endpoint from 1st route
                target = routes.get(0).getEndpoint();
            }
        } else {
            // is the endpoint a pattern or route id
            boolean scheme = endpoint.contains(":");
            boolean pattern = endpoint.endsWith("*");
            if (!scheme || pattern) {
                if (!scheme) {
                    endpoint = endpoint + "*";
                }
                for (Route route : getCamelContext().getRoutes()) {
                    Endpoint e = route.getEndpoint();
                    if (EndpointHelper.matchEndpoint(getCamelContext(), e.getEndpointUri(), endpoint)) {
                        target = e;
                        break;
                    }
                }
                if (target == null) {
                    // okay it may refer to a route id
                    for (Route route : getCamelContext().getRoutes()) {
                        String id = route.getRouteId();
                        Endpoint e = route.getEndpoint();
                        if (EndpointHelper.matchEndpoint(getCamelContext(), id, endpoint)) {
                            target = e;
                            break;
                        }
                    }
                }
            } else {
                target = getCamelContext().getEndpoint(endpoint);
            }
        }
        return target;
    }

    private Object prepareBody(String body) throws Exception {
        Object b = body;
        if (body.startsWith("file:")) {
            File file = new File(body.substring(5));
            b = new FileInputStream(file);
        }
        return b;
    }

    private Map<String, Object> prepareHeaders(Map<String, Object> options) {
        Map<String, Object> answer = new HashMap<>();
        options.forEach((k, v) -> {
            if (isCustomHeader(k)) {
                answer.put(k, v);
            }
        });
        return answer;
    }

    private static boolean isCustomHeader(String key) {
        return !BODY.equals(key) && !BODY_MAX_CHARS.equals(key) && !POLL.equals(key) && !POLL_TIMEOUT.equals(key)
                && !EXCHANGE_PATTERN.equals(key) && !ENDPOINT.equals(key)
                && !"CamelHttpPath".equals(key); // do not include ourself /q/dev/send
    }

}
