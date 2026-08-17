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
package org.apache.camel.impl.event;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Support class for serializing {@link CamelEvent} instances to JSON.
 */
public final class CamelEventJsonSupport {

    private CamelEventJsonSupport() {
    }

    public static Map<String, Object> asJSon(CamelEvent event) {
        JsonObject jo = new JsonObject();
        jo.put("type", event.getType().name());
        jo.put("eventClass", event.getClass().getSimpleName());
        if (event.getTimestamp() > 0) {
            jo.put("timestamp", event.getTimestamp());
        }
        jo.put("message", event.toString());

        if (event instanceof CamelEvent.CamelContextEvent contextEvent) {
            appendContext(jo, contextEvent.getContext());
        }
        if (event instanceof CamelEvent.RouteEvent routeEvent) {
            appendRoute(jo, routeEvent.getRoute());
        }
        if (event instanceof CamelEvent.ExchangeEvent exchangeEvent) {
            appendExchange(jo, exchangeEvent.getExchange());
        }
        if (event instanceof CamelEvent.StepEvent stepEvent) {
            jo.put("stepId", stepEvent.getStepId());
        }
        if (event instanceof CamelEvent.ExchangeSendingEvent sendingEvent) {
            appendEndpoint(jo, "endpointUri", sendingEvent.getEndpoint());
        }
        if (event instanceof CamelEvent.ExchangeSentEvent sentEvent) {
            appendEndpoint(jo, "endpointUri", sentEvent.getEndpoint());
            jo.put("timeTaken", sentEvent.getTimeTaken());
        }
        if (event instanceof CamelEvent.ExchangeRedeliveryEvent redeliveryEvent) {
            jo.put("attempt", redeliveryEvent.getAttempt());
            appendException(jo, redeliveryEvent.getExchange().getException());
        }
        if (event instanceof CamelEvent.ExchangeFailureEvent failureEvent) {
            appendFailureHandling(jo, failureEvent);
        }
        if (event instanceof CamelEvent.RouteReloadedEvent reloadedEvent) {
            jo.put("index", reloadedEvent.getIndex());
            jo.put("total", reloadedEvent.getTotal());
        }
        if (event instanceof CamelEvent.RouteRestartingEvent restartingEvent) {
            jo.put("attempt", restartingEvent.getAttempt());
        }
        if (event instanceof CamelEvent.RouteRestartingFailureEvent restartingFailureEvent) {
            jo.put("attempt", restartingFailureEvent.getAttempt());
            jo.put("exhausted", restartingFailureEvent.isExhausted());
        }
        if (event instanceof CamelEvent.ServiceEvent serviceEvent) {
            appendService(jo, serviceEvent.getService());
        }
        if (event instanceof ServiceStartupFailureEvent startupFailureEvent) {
            appendContext(jo, startupFailureEvent.getContext());
        } else if (event instanceof ServiceStopFailureEvent stopFailureEvent) {
            appendContext(jo, stopFailureEvent.getContext());
        }
        if (event instanceof CamelEvent.FailureEvent failureEvent) {
            appendException(jo, failureEvent.getCause());
        } else if (event instanceof CamelEvent.ExchangeFailureEvent) {
            Exchange exchange = ((CamelEvent.ExchangeFailureEvent) event).getExchange();
            appendException(jo, exchange.getException());
        }

        return jo;
    }

    public static String toJSon(CamelEvent event, int indent) {
        JsonObject jo = (JsonObject) asJSon(event);
        if (indent > 0) {
            return Jsoner.prettyPrint(jo.toJson(), indent);
        }
        return Jsoner.prettyPrint(jo.toJson());
    }

    private static void appendContext(JsonObject jo, CamelContext context) {
        if (context == null) {
            return;
        }
        jo.put("contextName", context.getName());
    }

    private static void appendRoute(JsonObject jo, Route route) {
        if (route == null) {
            return;
        }
        jo.put("routeId", route.getRouteId());
        if (ObjectHelper.isNotEmpty(route.getGroup())) {
            jo.put("routeGroup", route.getGroup());
        }
        if (route.getConsumer() != null && route.getConsumer().getEndpoint() != null) {
            jo.put("fromEndpointUri", sanitizeUri(route.getConsumer().getEndpoint().getEndpointUri()));
        }
    }

    private static void appendExchange(JsonObject jo, Exchange exchange) {
        if (exchange == null) {
            return;
        }
        jo.put("exchangeId", exchange.getExchangeId());
        if (exchange.getFromRouteId() != null) {
            jo.put("fromRouteId", exchange.getFromRouteId());
        }
        String routeId = exchange.getProperty(ExchangePropertyKey.FAILURE_ROUTE_ID, String.class);
        if (routeId == null) {
            routeId = exchange.getFromRouteId();
        }
        if (routeId != null) {
            jo.put("routeId", routeId);
        }
    }

    private static void appendEndpoint(JsonObject jo, String name, Endpoint endpoint) {
        if (endpoint != null) {
            jo.put(name, sanitizeUri(endpoint.getEndpointUri()));
        }
    }

    private static void appendFailureHandling(JsonObject jo, CamelEvent.ExchangeFailureEvent failureEvent) {
        jo.put("deadLetterChannel", failureEvent.isDeadLetterChannel());
        if (failureEvent.getDeadLetterUri() != null) {
            jo.put("deadLetterUri", sanitizeUri(failureEvent.getDeadLetterUri()));
        }
        Processor failureHandler = failureEvent.getFailureHandler();
        if (failureHandler != null) {
            jo.put("failureHandler", failureHandler.getClass().getName());
        }
    }

    private static void appendService(JsonObject jo, Object service) {
        if (service != null) {
            if (service instanceof String stringService) {
                jo.put("service", stringService);
            } else {
                jo.put("service", service.getClass().getName());
            }
        }
    }

    private static void appendException(JsonObject jo, Throwable cause) {
        if (cause == null) {
            return;
        }
        try {
            jo.put("exception", MessageHelper.dumpExceptionAsJSonObject(cause).get("exception"));
        } catch (Exception e) {
            jo.put("exceptionMessage", cause.getMessage());
        }
    }

    private static String sanitizeUri(String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return URISupport.sanitizeUri(uri);
        } catch (Exception e) {
            return uri;
        }
    }
}
