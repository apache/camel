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
package org.apache.camel.component.quickfixj;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.StartupListener;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.SessionSettings;

public class QuickfixjComponent extends DefaultComponent implements StartupListener {
    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjComponent.class);

    private final Object engineInstancesLock = new Object();
    private final Map<String, QuickfixjEngine> engines = new HashMap<String, QuickfixjEngine>();
    private final Map<String, QuickfixjEngine> provisionalEngines = new HashMap<String, QuickfixjEngine>();
    private final Map<String, QuickfixjEndpoint> endpoints = new HashMap<String, QuickfixjEndpoint>();

    private MessageStoreFactory messageStoreFactory;
    private LogFactory logFactory;
    private MessageFactory messageFactory;
    private Map<String, QuickfixjConfiguration> configurations = new HashMap<String, QuickfixjConfiguration>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Look up the engine instance based on the settings file ("remaining")
        QuickfixjEngine engine;
        synchronized (engineInstancesLock) {
            QuickfixjEndpoint endpoint = endpoints.get(uri);

            if (endpoint == null) {
                engine = engines.get(remaining);
                if (engine == null) {
                    engine = provisionalEngines.get(remaining);
                }
                if (engine == null) {
                    QuickfixjConfiguration configuration = configurations.get(remaining);
                    if (configuration != null) {
                        SessionSettings settings = configuration.createSessionSettings();
                        engine = new QuickfixjEngine(uri, settings, messageStoreFactory, logFactory, messageFactory);
                    } else {
                        engine = new QuickfixjEngine(uri, remaining, messageStoreFactory, logFactory, messageFactory);
                    }

                    // only start engine if CamelContext is already started, otherwise the engines gets started
                    // automatic later when CamelContext has been started using the StartupListener
                    if (getCamelContext().getStatus().isStarted()) {
                        startQuickfixjEngine(engine);
                        engines.put(remaining, engine);
                    } else {
                        // engines to be started later
                        provisionalEngines.put(remaining, engine);
                    }
                }

                endpoint = new QuickfixjEndpoint(engine, uri, this);
                engine.addEventListener(endpoint);
                endpoints.put(uri, endpoint);
            }

            return endpoint;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // we defer starting quickfix engines till the onCamelContextStarted callback
    }

    @Override
    protected void doStop() throws Exception {
        // stop engines when stopping component
        synchronized (engineInstancesLock) {
            for (QuickfixjEngine engine : engines.values()) {
                engine.stop();
            }
        }
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        // cleanup when shutting down
        engines.clear();
        provisionalEngines.clear();
        endpoints.clear();
        super.doShutdown();
    }

    private void startQuickfixjEngine(QuickfixjEngine engine) throws Exception {
        LOG.info("Starting QuickFIX/J engine: {}", engine.getUri());
        engine.start();
    }

    // Test Support
    Map<String, QuickfixjEngine> getEngines() {
        return Collections.unmodifiableMap(engines);
    }

    // Test Support
    Map<String, QuickfixjEngine> getProvisionalEngines() {
        return Collections.unmodifiableMap(provisionalEngines);
    }

    public void setMessageFactory(MessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public void setLogFactory(LogFactory logFactory) {
        this.logFactory = logFactory;
    }

    public void setMessageStoreFactory(MessageStoreFactory messageStoreFactory) {
        this.messageStoreFactory = messageStoreFactory;
    }

    /**
     * @deprecated Don't use as setting the {@code forcedShutdown} property had/has no effect.
     */
    @Deprecated
    public void setForcedShutdown(boolean forcedShutdown) {
    }

    public Map<String, QuickfixjConfiguration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, QuickfixjConfiguration> configurations) {
        this.configurations = configurations;
    }

    @Override
    public void onCamelContextStarted(CamelContext camelContext, boolean alreadyStarted) throws Exception {
        // only start quickfix engines when CamelContext have finished starting
        synchronized (engineInstancesLock) {
            for (QuickfixjEngine engine : engines.values()) {
                startQuickfixjEngine(engine);
            }
            for (Map.Entry<String, QuickfixjEngine> entry : provisionalEngines.entrySet()) {
                startQuickfixjEngine(entry.getValue());
                engines.put(entry.getKey(), entry.getValue());
                provisionalEngines.remove(entry.getKey());
            }
        }
    }
}
