/**
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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.XmlLineNumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for implementing custom {@link ReloadStrategy} SPI plugins.
 */
public abstract class ReloadStrategySupport extends ServiceSupport implements ReloadStrategy {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected Map<String, Object> cache;

    private CamelContext camelContext;

    private int succeeded;
    private int failed;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onReloadXml(CamelContext camelContext, String name, InputStream resource) {
        log.debug("Reloading routes from XML resource: {}", name);

        Document dom;
        String xml;
        try {
            xml = camelContext.getTypeConverter().mandatoryConvertTo(String.class, resource);
            // the JAXB model expects the spring namespace (even for blueprint)
            dom = XmlLineNumberParser.parseXml(new ByteArrayInputStream(xml.getBytes()), null, "camelContext,routeContext,routes", "http://camel.apache.org/schema/spring");
        } catch (Exception e) {
            failed++;
            log.warn("Cannot load the resource {} as XML", name);
            return;
        }

        ResourceState state = ObjectHelper.cast(ResourceState.class, cache.get(name));
        if (state == null) {
            state = new ResourceState(name, dom, xml);
            cache.put(name, state);
        }

        String oldXml = state.getXml();
        List<Integer> changed = StringHelper.changedLines(oldXml, xml);

        // find all <route> which are the routes
        NodeList list = dom.getElementsByTagName("route");

        // collect which routes are updated/skipped
        List<RouteDefinition> routes = new ArrayList<>();

        if (list != null && list.getLength() > 0) {
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);

                // what line number are this within
                String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
                if (lineNumber != null && lineNumberEnd != null && !changed.isEmpty()) {
                    int start = Integer.valueOf(lineNumber);
                    int end = Integer.valueOf(lineNumberEnd);

                    boolean within = withinChanged(start, end, changed);
                    if (within) {
                        log.debug("Updating route in lines: {}-{}", start, end);
                    } else {
                        log.debug("No changes to route in lines: {}-{}", start, end);
                        continue;
                    }
                }

                try {
                    // load from XML -> JAXB model and store as routes to be updated
                    RoutesDefinition loaded = ModelHelper.loadRoutesDefinition(camelContext, node);
                    if (!loaded.getRoutes().isEmpty()) {
                        routes.addAll(loaded.getRoutes());
                    }
                } catch (Exception e) {
                    failed++;
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }
        }

        if (!routes.isEmpty()) {
            try {
                boolean unassignedRouteIds = false;

                CollectionStringBuffer csb = new CollectionStringBuffer(",");
                // collect route ids and force assign ids if not in use
                for (RouteDefinition route : routes) {
                    unassignedRouteIds |= !route.hasCustomIdAssigned();
                    String id = route.idOrCreate(camelContext.getNodeIdFactory());
                    csb.append(id);
                }
                log.debug("Reloading routes: [{}] from XML resource: {}", csb, name);

                if (unassignedRouteIds) {
                    log.warn("Routes with no id's detected. Its recommended to assign id's to your routes so Camel can reload the routes correctly.");
                }
                // update the routes (add will remove and shutdown first)
                camelContext.addRouteDefinitions(routes);

                log.info("Reloaded routes: [{}] from XML resource: {}", csb, name);
            } catch (Exception e) {
                failed++;
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }

        }

        // update cache
        state = new ResourceState(name, dom, xml);
        cache.put(name, state);

        succeeded++;
    }

    private boolean withinChanged(int start, int end, List<Integer> changed) {
        for (int change : changed) {
            log.trace("Changed line: {} within {}-{}", change, start, end);
            if (change >= start && change <= end) {
                return true;
            }
        }
        return false;
    }

    @ManagedAttribute(description = "Number of reloads succeeded")
    public int getReloadCounter() {
        return succeeded;
    }

    @ManagedAttribute(description = "Number of reloads failed")
    public int getFailedCounter() {
        return failed;
    }
    
    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }
    
    public void setFailed(int failed) {
        this.failed = failed;
    }

    @ManagedOperation(description = "Reset counters")
    public void resetCounters() {
        succeeded = 0;
        failed = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        // noop
        cache = LRUCacheFactory.newLRUCache(100);
    }

    @Override
    protected void doStop() throws Exception {
        cache.clear();
    }

    /**
     * To keep state of last reloaded resource
     */
    private static final class ResourceState {
        private final String name;
        private final Document dom;
        private final String xml;

        ResourceState(String name, Document dom, String xml) {
            this.name = name;
            this.dom = dom;
            this.xml = xml;
        }

        public String getName() {
            return name;
        }

        public Document getDom() {
            return dom;
        }

        public String getXml() {
            return xml;
        }
    }

}
