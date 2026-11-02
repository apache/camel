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
package org.apache.camel.component.optaplanner;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.kie.api.KieServices;

@Component("optaplanner")
public class OptaPlannerComponent extends DefaultComponent {

    // the consumers listening for the best solutions, per problemId, in this CamelContext
    private final Map<Long, Set<OptaplannerSolutionEventListener>> solutionListeners = new ConcurrentHashMap<>();

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        OptaPlannerConfiguration configuration = new OptaPlannerConfiguration();
        configuration.setConfigFile(remaining);

        // [CAMEL-11889] Kie assumes that the TCCL can load its services
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            KieServices kieServices = KieServices.Factory.get();
            ObjectHelper.notNull(kieServices, "KieServices");
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }

        OptaPlannerEndpoint answer = new OptaPlannerEndpoint(uri, this, configuration);
        setProperties(answer, parameters);
        return answer;
    }

    protected Set<OptaplannerSolutionEventListener> getSolutionEventListeners(Long problemId) {
        return solutionListeners.get(problemId);
    }

    protected void addSolutionEventListener(Long problemId, OptaplannerSolutionEventListener listener) {
        solutionListeners.compute(problemId, (k, listeners) -> {
            if (listeners == null) {
                listeners = new CopyOnWriteArraySet<>();
            }
            listeners.add(listener);
            return listeners;
        });
    }

    protected void removeSolutionEventListener(Long problemId, OptaplannerSolutionEventListener listener) {
        solutionListeners.computeIfPresent(problemId, (k, listeners) -> {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                return null;
            }
            return listeners;
        });
    }
}
