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
package org.apache.camel.component.ai.tool;

import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that registers a Camel route as an AI tool in the {@link AiToolRegistry} on start and deregisters on stop.
 *
 * @since 4.22
 */
public class AiToolConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolConsumer.class);

    private final String toolName;
    private final AiToolConfiguration configuration;
    private AiToolSpec registeredSpec;
    private String[] registeredTags;
    private boolean registeredInDefaultPool;

    public AiToolConsumer(AiToolEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.toolName = endpoint.getToolName();
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Map<String, String> params = configuration.getParameters();
        Map<String, AiToolParameterHelper.ParameterDef> parameterDefs
                = (params != null && !params.isEmpty())
                        ? AiToolParameterHelper.parseParameterMetadata(params)
                        : Map.of();

        String jsonSchema = !parameterDefs.isEmpty()
                ? AiToolParameterHelper.buildJsonSchemaFromDefs(parameterDefs)
                : null;

        registeredSpec = new AiToolSpec(
                toolName, configuration.getDescription(), parameterDefs, jsonSchema, this);

        String tags = configuration.getTags();
        String[] parsedTags = (tags != null && !tags.isBlank())
                ? AiToolParameterHelper.splitTags(tags)
                : null;
        if (parsedTags != null && parsedTags.length > 0) {
            registeredTags = parsedTags;
            registeredInDefaultPool = false;
        } else {
            registeredTags = null;
            registeredInDefaultPool = true;
        }

        register();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (registeredSpec != null) {
            deregister();
        }
        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();
        if (registeredSpec != null) {
            register();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (registeredSpec != null) {
            deregister();
            registeredSpec = null;
            registeredTags = null;
            registeredInDefaultPool = false;
        }
        super.doStop();
    }

    private void register() {
        AiToolRegistry registry = AiToolRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Registering tool '{}' with tag '{}'", toolName, tag);
                registry.put(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            LOG.debug("Registering tool '{}' in default pool (no tags)", toolName);
            registry.putDefault(registeredSpec);
        }
    }

    private void deregister() {
        AiToolRegistry registry = AiToolRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Removing tool '{}' from tag '{}'", registeredSpec.getName(), tag);
                registry.remove(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            LOG.debug("Removing tool '{}' from default pool", registeredSpec.getName());
            registry.removeDefault(registeredSpec);
        }
    }
}
