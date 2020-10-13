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
package org.apache.camel.component.servicenow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public final class ServiceNowHelper {
    private ServiceNowHelper() {
    }

    public static void findOffsets(Response response, BiConsumer<String, Object> consumer) throws Exception {
        List<String> links = response.getStringHeaders().get(HttpHeaders.LINK);
        if (links != null) {
            for (String link : links) {
                String[] parts = link.split(";");
                if (parts.length != 2) {
                    continue;
                }

                // Sanitize parts
                String uri = StringHelper.between(parts[0], "<", ">");
                String rel = StringHelper.removeQuotes(StringHelper.after(parts[1], "="));

                Map<String, Object> query = URISupport.parseQuery(uri);
                Object offset = query.get(ServiceNowParams.SYSPARM_OFFSET.getId());

                if (offset != null) {
                    switch (rel) {
                        case ServiceNowConstants.LINK_FIRST:
                            consumer.accept(ServiceNowConstants.OFFSET_FIRST, offset);
                            break;
                        case ServiceNowConstants.LINK_LAST:
                            consumer.accept(ServiceNowConstants.OFFSET_LAST, offset);
                            break;
                        case ServiceNowConstants.LINK_NEXT:
                            consumer.accept(ServiceNowConstants.OFFSET_NEXT, offset);
                            break;
                        case ServiceNowConstants.LINK_PREV:
                            consumer.accept(ServiceNowConstants.OFFSET_PREV, offset);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    public static Optional<String> findOffset(Response response, String type) throws Exception {
        List<String> links = response.getStringHeaders().get(HttpHeaders.LINK);
        if (links != null) {
            for (String link : links) {
                String[] parts = link.split(";");
                if (parts.length != 2) {
                    continue;
                }

                // Sanitize parts
                String uri = StringHelper.between(parts[0], "<", ">");
                String rel = StringHelper.removeQuotes(StringHelper.after(parts[1], "="));

                Map<String, Object> query = URISupport.parseQuery(uri);
                Object offset = query.get(ServiceNowParams.SYSPARM_OFFSET.getId());

                if (offset != null && type.equals(rel)) {
                    return Optional.of(offset.toString());
                }
            }
        }

        return Optional.empty();
    }
}
