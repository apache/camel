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
package org.apache.camel.component.platform.http.main;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.StartupListener;
import org.apache.camel.component.platform.http.HttpEndpointModel;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainHttpServerUtil {

    private static final Logger LOG = LoggerFactory.getLogger(MainHttpServerUtil.class);

    protected static void setupStartupSummary(
            CamelContext camelContext, Set<HttpEndpointModel> endpoints, int serverPort, String header)
            throws Exception {
        camelContext.addStartupListener(new StartupListener() {
            private volatile Set<HttpEndpointModel> last;

            private void logSummary() {
                if (endpoints.isEmpty()) {
                    return;
                }
                // log only if changed
                if (last == null || last.size() != endpoints.size() || !last.containsAll(endpoints)) {
                    LOG.info(header);
                    int longestEndpoint = 0;
                    int longestVerbs = 0;
                    for (HttpEndpointModel u : endpoints) {
                        String endpoint = getEndpoint(u);
                        if (endpoint.length() > longestEndpoint) {
                            longestEndpoint = endpoint.length();
                        }
                        if (u.getVerbs() != null) {
                            if (u.getVerbs().length() > longestVerbs) {
                                longestVerbs = u.getVerbs().length();
                            }
                        }
                    }
                    longestEndpoint += 3; // add some spacing
                    longestVerbs += 2; // add parenthesis
                    longestVerbs = Math.max(8, longestVerbs); // minimum length
                    String formatTemplate = "%-" + longestEndpoint + "s %-" + longestVerbs + "s %s";
                    for (HttpEndpointModel u : endpoints) {
                        String endpoint = getEndpoint(u);
                        String formattedVerbs = "";
                        if (u.getVerbs() != null) {
                            formattedVerbs = "(" + u.getVerbs() + ")";
                        }
                        String formattedMediaTypes = "";
                        if (u.getConsumes() != null || u.getProduces() != null) {
                            formattedMediaTypes = String.format("(%s%s%s)",
                                    u.getConsumes() != null ? "accept:" + u.getConsumes() : "",
                                    u.getProduces() != null && u.getConsumes() != null ? " " : "",
                                    u.getProduces() != null ? "produce:" + u.getProduces() : "");
                        }
                        LOG.info("    {}", String.format(formatTemplate, endpoint, formattedVerbs, formattedMediaTypes));
                    }
                }

                // use a defensive copy of last known endpoints
                last = new HashSet<>(endpoints);
            }

            private String getEndpoint(HttpEndpointModel httpEndpointModel) {
                return "http://0.0.0.0:" + serverPort + httpEndpointModel.getUri();
            }

            @Override
            public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) {
                if (alreadyStarted) {
                    logSummary();
                }
                camelContext.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
                    @Override
                    public boolean isEnabled(CamelEvent event) {
                        return event instanceof CamelEvent.CamelContextStartedEvent
                                || event instanceof CamelEvent.RouteReloadedEvent;
                    }

                    @Override
                    public void notify(CamelEvent event) {
                        // when reloading then there may be more routes in the same batch, so we only want
                        // to log the summary at the end
                        if (event instanceof CamelEvent.RouteReloadedEvent re) {
                            if (re.getIndex() < re.getTotal()) {
                                return;
                            }
                        }
                        logSummary();
                    }
                });
            }
        });
    }

}
