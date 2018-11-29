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

package org.apache.camel.component.jbpm.workitem;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.api.executor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CamelCommand that uses the global {@link CamelContext} registered on the {@link ServiceRegistry}.
 */
public class GlobalContextCamelCommand extends AbstractCamelCommand {

    private static final String GLOBAL_CAMEL_CONTEXT_SERVICE_KEY = "GlobalCamelService";
    
    private final ProducerTemplate globalContextProducerTemplate;
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalContextCamelCommand.class);
    
    public GlobalContextCamelCommand() {
        CamelContext globalCamelContext = (CamelContext) ServiceRegistry.get().service(GLOBAL_CAMEL_CONTEXT_SERVICE_KEY);
        // TODO: Should we allow to set the maximumCacheSize on the producer?
        this.globalContextProducerTemplate = globalCamelContext.createProducerTemplate();
    }
    
    @Override
    protected ProducerTemplate getProducerTemplate(CommandContext ctx) {
        return globalContextProducerTemplate;
    }
    
    @Override
    public void close() {
        try {
            this.globalContextProducerTemplate.stop();
        } catch (Exception e) {
            logger.warn("Error encountered while closing the Camel Producer Template.", e);
            // Not much we can do here, so swallowing exception.
        }
    }
    

}
