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

import org.apache.camel.CamelContext;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.eventregistry.impl.configurator.EventRegistryEngineConfigurator;

public class FlowableEngineUtil {

    protected static ProcessEngine processEngine;
    protected static EventRegistryEngineConfiguration eventRegistryEngineConfiguration;
    protected static CamelChannelModelProcessor camelChannelModelProcessor;

    public static ProcessEngine getProcessEngine(CamelContext camelContext) {
        if (processEngine == null) {
            buildEngine(camelContext);
        } else {
            camelChannelModelProcessor.setCamelContext(camelContext);
        }

        return processEngine;
    }

    public static EventRegistryEngineConfiguration getEventRegistryEngineConfiguration() {
        return eventRegistryEngineConfiguration;
    }

    protected static void buildEngine(CamelContext camelContext) {
        eventRegistryEngineConfiguration = new EventRegistryEngineConfiguration();
        eventRegistryEngineConfiguration.setDatabaseSchemaUpdate("true");
        eventRegistryEngineConfiguration.setEnableEventRegistryChangeDetection(false);
        camelChannelModelProcessor = new CamelChannelModelProcessor(camelContext);
        eventRegistryEngineConfiguration.addChannelModelProcessor(camelChannelModelProcessor);

        EventRegistryEngineConfigurator eventRegistryEngineConfigurator = new EventRegistryEngineConfigurator();
        eventRegistryEngineConfigurator.setEventEngineConfiguration(eventRegistryEngineConfiguration);

        ProcessEngineConfiguration processEngineConfiguration = new StandaloneInMemProcessEngineConfiguration();
        processEngineConfiguration.setDatabaseSchemaUpdate("true");
        processEngineConfiguration.setAsyncExecutorActivate(false);
        processEngineConfiguration.setEventRegistryConfigurator(eventRegistryEngineConfigurator);
        processEngine = processEngineConfiguration.buildProcessEngine();
    }
}
