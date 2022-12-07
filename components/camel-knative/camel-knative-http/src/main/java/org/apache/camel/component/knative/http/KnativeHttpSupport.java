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
package org.apache.camel.component.knative.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import io.vertx.core.http.HttpServerRequest;
import org.apache.camel.Message;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.knative.spi.KnativeResource;

public final class KnativeHttpSupport {
    private KnativeHttpSupport() {
    }

    @SuppressWarnings("unchecked")
    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        if (headers.containsKey(key)) {
            Object existing = headers.get(key);
            List<Object> list;
            if (existing instanceof List) {
                list = (List<Object>) existing;
            } else {
                list = new ArrayList<>();
                list.add(existing);
            }
            list.add(value);
            value = list;
        }

        headers.put(key, value);
    }

    public static Predicate<HttpServerRequest> createFilter(CloudEvent cloudEvent, KnativeResource resource) {
        final Map<String, String> filters = new HashMap<>();

        for (Map.Entry<String, String> entry : resource.getFilters().entrySet()) {
            cloudEvent.attribute(entry.getKey())
                    .map(CloudEvent.Attribute::http)
                    .ifPresentOrElse(
                            k -> filters.put(k, entry.getValue()),
                            () -> filters.put(entry.getKey(), entry.getValue()));
        }

        return (HttpServerRequest request) -> {
            if (filters.isEmpty()) {
                return true;
            }

            for (Map.Entry<String, String> entry : filters.entrySet()) {
                final List<String> values = request.headers().getAll(entry.getKey());
                if (values.isEmpty()) {
                    return false;
                }

                String val = values.get(values.size() - 1);
                int idx = val.lastIndexOf(',');

                if (values.size() == 1 && idx != -1) {
                    val = val.substring(idx + 1);
                    val = val.trim();
                }

                boolean matches = Objects.equals(entry.getValue(), val) || val.matches(entry.getValue());
                if (!matches) {
                    return false;
                }
            }

            return true;
        };
    }

    /**
     * Removes cloud event headers at the end of the processing.
     */
    public static void removeCloudEventHeaders(CloudEvent ce, Message message) {
        // remove CloudEvent headers
        for (CloudEvent.Attribute attr : ce.attributes()) {
            message.removeHeader(attr.http());
            message.removeHeader(attr.id());
        }
    }

    /**
     * Remap camel headers to cloud event http headers.
     */
    public static void remapCloudEventHeaders(CloudEvent ce, Message message) {
        // remap CloudEvent camel --> http
        for (CloudEvent.Attribute attr : ce.attributes()) {
            Object value = message.getHeader(attr.id());
            if (value != null) {
                message.setHeader(attr.http(), value);
            }
        }
    }

}
