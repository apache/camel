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
package org.apache.camel.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.telemetry.decorators.AbstractSpanDecorator;
import org.apache.camel.telemetry.decorators.ProcessorSpanDecorator;
import org.apache.camel.util.StringHelper;

/**
 * Implementation of a SpanDecoratorManager
 */
public class SpanDecoratorManagerImpl implements SpanDecoratorManager {

    protected static final Map<String, SpanDecorator> DECORATORS = new HashMap<>();
    protected static SpanDecorator DEFAULT = new AbstractSpanDecorator() {

        @Override
        public String getComponent() {
            return "default";
        }

        @Override
        public String getComponentClassName() {
            return "default";
        }

    };

    static {
        ServiceLoader.load(SpanDecorator.class).forEach(d -> {
            SpanDecorator existing = DECORATORS.get(d.getComponent());
            // Add span decorator if no existing decorator for the component,
            // or if derived from the existing decorator's class, allowing
            // custom decorators to be added if they extend the standard
            // decorators
            if (existing == null || existing.getClass().isInstance(d)) {
                DECORATORS.put(d.getComponent(), d);
            }
        });
    }

    @Override
    public SpanDecorator get(Endpoint endpoint) {
        SpanDecorator sd = null;

        String uri = endpoint.getEndpointUri();
        String[] splitURI = StringHelper.splitOnCharacter(uri, ":", 2);
        if (splitURI[1] != null) {
            String scheme = splitURI[0];
            sd = DECORATORS.get(scheme);
        }
        if (sd == null && endpoint instanceof DefaultEndpoint de) {
            Component comp = de.getComponent();
            String fqn = comp.getClass().getName();
            // lookup via FQN
            sd = DECORATORS.values().stream().filter(d -> fqn.equals(d.getComponentClassName())).findFirst()
                    .orElse(null);
        }
        if (sd == null) {
            sd = SpanDecoratorManagerImpl.DEFAULT;
        }

        return sd;
    }

    @Override
    public SpanDecorator get(String processorName) {
        return new ProcessorSpanDecorator(processorName);
    }

}
