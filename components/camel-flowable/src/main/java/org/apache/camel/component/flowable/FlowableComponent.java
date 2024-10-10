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
package org.apache.camel.component.flowable;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.engine.ProcessEngine;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;

@Component("flowable")
public class FlowableComponent extends DefaultComponent {

    protected EventRegistryEngineConfiguration eventRegistryEngineConfiguration;

    public FlowableComponent() {
    }

    @Override
    public void setCamelContext(CamelContext context) {
        super.setCamelContext(context);
        ProcessEngine processEngine = getByType(context, ProcessEngine.class);
        this.eventRegistryEngineConfiguration = (EventRegistryEngineConfiguration) processEngine.getProcessEngineConfiguration()
                .getEngineConfigurations().get(EngineConfigurationConstants.KEY_EVENT_REGISTRY_CONFIG);
    }

    private <T> T getByType(CamelContext ctx, Class<T> kls) {
        Map<String, T> looked = ctx.getRegistry().findByTypeWithName(kls);
        if (looked.isEmpty()) {
            return null;
        }
        return looked.values().iterator().next();

    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        throw new FlowableException("not supported");
    }
}
