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
package org.apache.camel.component.kamelet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kamelet.Kamelet.PARAM_ROUTE_ID;
import static org.apache.camel.component.kamelet.Kamelet.PARAM_TEMPLATE_ID;

/**
 * The Kamelet Component provides support for materializing routes templates.
 */
@Component(Kamelet.SCHEME)
public class KameletComponent extends DefaultComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(KameletComponent.class);

    private final LifecycleHandler lifecycleHandler = new LifecycleHandler();

    // active consumers
    private final Map<String, KameletConsumer> consumers = new HashMap<>();
    // active kamelet EIPs
    private final Map<String, Processor> kameletEips = new ConcurrentHashMap<>();

    // counter that is used for producers to keep track if any consumer was added/removed since they last checked
    // this is used for optimization to avoid each producer to get consumer for each message processed
    // (locking via synchronized, and then lookup in the map as the cost)
    // consumers and producers are only added/removed during startup/shutdown or if routes is manually controlled
    private volatile int stateCounter;

    @Metadata(label = "producer", defaultValue = "true")
    private boolean block = true;
    @Metadata(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;

    @Metadata
    private Map<String, Properties> templateProperties;
    @Metadata
    private Map<String, Properties> routeProperties;

    public KameletComponent() {
    }

    public void addKameletEip(String key, Processor callback) {
        kameletEips.put(key, callback);
    }

    public Processor removeKameletEip(String key) {
        return kameletEips.remove(key);
    }

    public Processor getKameletEip(String key) {
        return kameletEips.get(key);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final String templateId = Kamelet.extractTemplateId(getCamelContext(), remaining, parameters);
        final String routeId = Kamelet.extractRouteId(getCamelContext(), remaining, parameters);

        parameters.remove(PARAM_TEMPLATE_ID);
        parameters.remove(PARAM_ROUTE_ID);

        final KameletEndpoint endpoint;

        if (Kamelet.SOURCE_ID.equals(remaining) || Kamelet.SINK_ID.equals(remaining)) {
            //
            // if remaining is either `source` or `sink' then it is a virtual
            // endpoint that is used inside the kamelet definition to mark it
            // as in/out endpoint.
            //
            // The following snippet defines a template which will act as a
            // consumer for this Kamelet:
            //
            //     from("kamelet:source")
            //         .to("log:info")
            //
            // The following snippet defines a template which will act as a
            // producer for this Kamelet:
            //
            //     from("telegram:bots")
            //         .to("kamelet:sink")
            //
            // Note that at the moment, there's no enforcement around `source`
            // and `sink' to be defined on the right side (producer or consumer)
            //
            endpoint = new KameletEndpoint(uri, this, templateId, routeId);

            // forward component properties
            endpoint.setBlock(block);
            endpoint.setTimeout(timeout);

            // set endpoint specific properties
            setProperties(endpoint, parameters);
        } else {
            endpoint = new KameletEndpoint(uri, this, templateId, routeId) {
                @Override
                protected void doInit() throws Exception {
                    super.doInit();
                    //
                    // since this is the real kamelet, then we need to hand it
                    // over to the tracker.
                    //
                    lifecycleHandler.track(this);
                }
            };

            // forward component properties
            endpoint.setBlock(block);
            endpoint.setTimeout(timeout);

            // set and remove endpoint specific properties
            setProperties(endpoint, parameters);

            Map<String, Object> kameletProperties = new HashMap<>();

            //
            // Load properties from the component configuration. Template and route specific properties
            // can be set through properties, as example:
            //
            //     camel.component.kamelet.template-properties[templateId].key = val
            //     camel.component.kamelet.route-properties[routeId].key = val
            //
            if (templateProperties != null) {
                Properties props = templateProperties.get(templateId);
                if (props != null) {
                    props.stringPropertyNames().forEach(name -> kameletProperties.put(name, props.get(name)));
                }
            }
            if (routeProperties != null) {
                Properties props = routeProperties.get(routeId);
                if (props != null) {
                    props.stringPropertyNames().forEach(name -> kameletProperties.put(name, props.get(name)));
                }
            }

            //
            // We can't mix configuration styles so if properties are not configured through the component,
            // then fallback to the old - deprecated - style.
            //
            if (kameletProperties.isEmpty()) {
                //
                // The properties for the kamelets are determined by global properties
                // and local endpoint parameters,
                //
                // Global parameters are loaded in the following order:
                //
                //   camel.kamelet." + templateId
                //   camel.kamelet." + templateId + "." routeId
                //
                Kamelet.extractKameletProperties(getCamelContext(), kameletProperties, templateId, routeId);
            }

            //
            // Uri params have the highest precedence
            //
            kameletProperties.putAll(parameters);

            //
            // And finally we have some specific properties that cannot be changed by the user.
            //
            kameletProperties.put(PARAM_TEMPLATE_ID, templateId);
            kameletProperties.put(PARAM_ROUTE_ID, routeId);

            // set kamelet specific properties
            endpoint.setKameletProperties(kameletProperties);

            //
            // Add a custom converter to convert a RouteTemplateDefinition to a RouteDefinition
            // and make sure consumerU URIs are unique.
            //
            getCamelContext().adapt(ModelCamelContext.class).addRouteTemplateDefinitionConverter(
                    templateId,
                    Kamelet::templateToRoute);
        }

        return endpoint;
    }

    @Override
    protected boolean resolveRawParameterValues() {
        return false;
    }

    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a kamelet endpoint which has no active consumer, then we can tell the producer to block
     * and wait for the consumer to become active.
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Map<String, Properties> getTemplateProperties() {
        return templateProperties;
    }

    /**
     * Set template local parameters.
     */
    public void setTemplateProperties(Map<String, Properties> templateProperties) {
        this.templateProperties = templateProperties;
    }

    public Map<String, Properties> getRouteProperties() {
        return routeProperties;
    }

    /**
     * Set route local parameters.
     */
    public void setRouteProperties(Map<String, Properties> routeProperties) {
        this.routeProperties = routeProperties;
    }

    int getStateCounter() {
        return stateCounter;
    }

    public void addConsumer(String key, KameletConsumer consumer) {
        synchronized (consumers) {
            if (consumers.putIfAbsent(key, consumer) != null) {
                throw new IllegalArgumentException(
                        "Cannot add a 2nd consumer to the same endpoint: " + key
                                                   + ". KameletEndpoint only allows one consumer.");
            }
            // state changed so inc counter
            stateCounter++;
            consumers.notifyAll();
        }
    }

    public void removeConsumer(String key, KameletConsumer consumer) {
        synchronized (consumers) {
            consumers.remove(key, consumer);
            // state changed so inc counter
            stateCounter++;
            consumers.notifyAll();
        }
    }

    protected KameletConsumer getConsumer(String key, boolean block, long timeout) throws InterruptedException {
        synchronized (consumers) {
            KameletConsumer answer = consumers.get(key);
            if (answer == null && block) {
                StopWatch watch = new StopWatch();
                for (;;) {
                    answer = consumers.get(key);
                    if (answer != null) {
                        break;
                    }
                    long rem = timeout - watch.taken();
                    if (rem <= 0) {
                        break;
                    }
                    consumers.wait(rem);
                }
            }
            return answer;
        }
    }

    @Override
    protected void doInit() throws Exception {
        getCamelContext().addLifecycleStrategy(lifecycleHandler);

        if (getCamelContext().isRunAllowed()) {
            lifecycleHandler.setInitialized(true);
        }

        super.doInit();
    }

    @Override
    protected void doShutdown() throws Exception {
        getCamelContext().getLifecycleStrategies().remove(lifecycleHandler);

        ServiceHelper.stopAndShutdownService(consumers);
        consumers.clear();
        kameletEips.clear();
        super.doShutdown();
    }

    /*
     * This LifecycleHandler is used to keep track of created kamelet endpoints during startup as
     * we need to defer create routes from templates until camel context has finished loading
     * all routes and whatnot.
     *
     * Once the camel context is initialized all the endpoint tracked by this LifecycleHandler will
     * be used to create routes from templates.
     */
    private static class LifecycleHandler extends LifecycleStrategySupport {
        private final List<KameletEndpoint> endpoints;
        private final AtomicBoolean initialized;

        public LifecycleHandler() {
            this.endpoints = new ArrayList<>();
            this.initialized = new AtomicBoolean();
        }

        public static void createRouteForEndpoint(KameletEndpoint endpoint) throws Exception {
            LOGGER.debug("Creating route from template={} and id={}", endpoint.getTemplateId(), endpoint.getRouteId());

            final ModelCamelContext context = endpoint.getCamelContext().adapt(ModelCamelContext.class);
            final String id = context.addRouteFromTemplate(endpoint.getRouteId(), endpoint.getTemplateId(),
                    endpoint.getKameletProperties());
            final RouteDefinition def = context.getRouteDefinition(id);

            if (!def.isPrepared()) {
                context.startRouteDefinitions(Collections.singletonList(def));
            }

            LOGGER.debug("Route with id={} created from template={}", id, endpoint.getTemplateId());
        }

        @Override
        public void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
            if (this.initialized.compareAndSet(false, true)) {
                for (KameletEndpoint endpoint : endpoints) {
                    try {
                        createRouteForEndpoint(endpoint);
                    } catch (Exception e) {
                        throw new VetoCamelContextStartException(
                                "Failure creating route from template: " + endpoint.getTemplateId(), e, context);
                    }
                }

                endpoints.clear();
            }
        }

        public void setInitialized(boolean initialized) {
            this.initialized.set(initialized);
        }

        public void track(KameletEndpoint endpoint) {
            if (this.initialized.get()) {
                try {
                    createRouteForEndpoint(endpoint);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            } else {
                LOGGER.debug("Tracking route template={} and id={}", endpoint.getTemplateId(), endpoint.getRouteId());
                this.endpoints.add(endpoint);
            }
        }
    }

}
