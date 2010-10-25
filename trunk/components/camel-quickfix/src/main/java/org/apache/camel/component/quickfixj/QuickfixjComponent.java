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

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuickfixjComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjComponent.class);

    private final Object engineInstancesLock = new Object();
    private final Map<String, QuickfixjEngine> engines = new HashMap<String, QuickfixjEngine>();
    private final Map<String, QuickfixjEndpoint> endpoints = new HashMap<String, QuickfixjEndpoint>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Look up the engine instance based on the settings file ("remaining")
        QuickfixjEngine engine;
        synchronized (engineInstancesLock) {
            QuickfixjEndpoint endpoint = endpoints.get(uri);

            if (endpoint == null) {
                engine = engines.get(remaining);
                if (engine == null) {
                    LOG.info("Creating QuickFIX/J engine using settings: " + remaining);
                    engine = new QuickfixjEngine(remaining, false);
                    engines.put(remaining, engine);
                    if (isStarted()) {
                        startQuickfixjEngine(engine);
                    }
                }
                
                endpoint = new QuickfixjEndpoint(uri, getCamelContext());
                engine.addEventListener(endpoint);
                endpoints.put(uri, endpoint);
            }
            
            return endpoint;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("QuickFIX/J component started");
        synchronized (engineInstancesLock) {
            for (QuickfixjEngine engine : engines.values()) {
                startQuickfixjEngine(engine);            
            }
        }
    }

    private void startQuickfixjEngine(QuickfixjEngine engine) throws Exception {
        LOG.info("Starting QuickFIX/J engine: " + engine.getSettingsResourceName());
        engine.start();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        synchronized (engineInstancesLock) {
            for (QuickfixjEngine engine : engines.values()) {
                engine.stop();           
            }
        }
        LOG.info("QuickFIX/J component stopped");
    }

    // Test Support
    Map<String, QuickfixjEngine> getEngines() {
        return Collections.unmodifiableMap(engines);
    }
}
