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
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "send", displayName = "Camel Send", description = "Send messages to endpoints")
@Configurer(extended = true)
public class SendDevConsole extends AbstractDevConsole {

    private ProducerTemplate producer;
    private ConsumerTemplate consumer;

    @Metadata(defaultValue = "32768",
              description = "Maximum size of the message body to include in the dump")
    private int bodyMaxChars = 32 * 1024;

    @Metadata(defaultValue = "20000", label = "advanced",
              description = "Timeout when using poll mode")
    private int pollTimeout = 20000;

    /**
     * Maximum size of the message body to include in the dump
     */
    public static final String BODY_MAX_CHARS = "bodyMaxChars";

    /**
     * The message body to send. Can refer to files using file: prefix
     */
    public static final String BODY = "body";

    /**
     * Whether to poll message from the endpoint instead of sending
     */
    public static final String POLL = "poll";

    /**
     * Timeout when using poll mode
     */
    public static final String POLL_TIMEOUT = "pollTimeout";

    /**
     * Exchange pattern when sending
     */
    public static final String EXCHANGE_PATTERN = "exchangePattern";

    /**
     * Endpoint for where to send messages (can also refer to a route id, endpoint pattern).
     */
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
        String endpoint = (String) options.get(ENDPOINT);
        String body = (String) options.getOrDefault(BODY, "");
        String exchangePattern = (String) options.get(EXCHANGE_PATTERN);
        boolean poll = "true".equals(options.get(POLL));
        int timeout = Integer.parseInt((String) options.getOrDefault(POLL_TIMEOUT, String.valueOf(pollTimeout)));
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
            sb.append(String.format("\n    Endpoint: %s", target));
        } else if (endpoint != null) {
            sb.append(String.format("\n    Endpoint: %s", endpoint));
        }
        sb.append(String.format("\n    Status: %s", status));
        sb.append(String.format("\n    Elapsed: %s", TimeUtils.printDuration(taken)));
        if (cause != null) {
            sb.append(String.format("\n    Error Message: %s", cause.getMessage()));
            final String stackTrace = ExceptionHelper.stackTraceToString(cause);
            sb.append("\n\n");
            sb.append(stackTrace);
            sb.append("\n\n");
        }
        if (out != null && (poll || "InOut".equals(exchangePattern))) {
            sb.append("\n    Response Message:\n");
            int maxChars = Integer.parseInt((String) options.getOrDefault(BODY_MAX_CHARS, "" + bodyMaxChars));
            String json
                    = MessageHelper.dumpAsJSon(out.getMessage(), false, false, true, 2, true, true, true,
                            maxChars, true);
            sb.append(json);
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        StopWatch watch = new StopWatch();
        long timestamp = System.currentTimeMillis();
        String endpoint = (String) options.get(ENDPOINT);
        String body = (String) options.getOrDefault(BODY, "");
        String exchangePattern = (String) options.get(EXCHANGE_PATTERN);
        boolean poll = "true".equals(options.get(POLL));
        int timeout = Integer.parseInt((String) options.getOrDefault(POLL_TIMEOUT, String.valueOf(pollTimeout)));
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

        root.put("timestamp", timestamp);
        root.put("status", status);
        root.put("elapsed", taken);
        if (target != null) {
            root.put("endpoint", target.toString());
        } else if (endpoint != null) {
            root.put("endpoint", endpoint);
        }
        if (cause != null) {
            // avoid double wrap
            root.put("exception", MessageHelper.dumpExceptionAsJSonObject(cause).getMap("exception"));
        }
        if (out != null && (poll || "InOut".equals(exchangePattern))) {
            root.put("exchangeId", out.getExchangeId());
            int maxChars = Integer.parseInt((String) options.getOrDefault(BODY_MAX_CHARS, "" + bodyMaxChars));
            // avoid double wrap
            root.put("message", MessageHelper.dumpAsJSonObject(out.getMessage(), true, true, true, true, true, true,
                    maxChars).getMap("message"));
        }

        return root;
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

    private Map prepareHeaders(Map<String, Object> options) {
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
                && !EXCHANGE_PATTERN.equals(key) && !ENDPOINT.equals(key);
    }

}
