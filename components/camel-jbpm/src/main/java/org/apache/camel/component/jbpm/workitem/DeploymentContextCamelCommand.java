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
package org.apache.camel.component.jbpm.workitem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.api.executor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CamelCommand that uses the {@link CamelContext} registered on the {@link ServiceRegistry} for this specific deployment.
 */
public class DeploymentContextCamelCommand extends AbstractCamelCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentContextCamelCommand.class);

    private final Map<String, ProducerTemplate> templates = new ConcurrentHashMap<>();

    @Override
    protected ProducerTemplate getProducerTemplate(CommandContext ctx) {
        String deploymentId = (String) ctx.getData("deploymentId");
        ProducerTemplate template = templates.get(deploymentId);

        if (template == null) {
            synchronized (this) {
                template = templates.get(deploymentId);
                if (template == null) {
                    CamelContext deploymentCamelContext = (CamelContext) ServiceRegistry.get()
                            .service(deploymentId + JBPMConstants.DEPLOYMENT_CAMEL_CONTEXT_SERVICE_KEY_POSTFIX);
                    template = deploymentCamelContext.createProducerTemplate();
                    templates.put(deploymentId, template);
                }
            }
        }
        return template;
    }

    @Override
    public void close() {
        for (ProducerTemplate nextTemplate : templates.values()) {
            try {
                nextTemplate.stop();
            } catch (Exception e) {
                LOGGER.warn("Error encountered while closing the Camel Producer Template.", e);
                // Not much we can do here, so swallowing exception.
            }
        }

    }

}
