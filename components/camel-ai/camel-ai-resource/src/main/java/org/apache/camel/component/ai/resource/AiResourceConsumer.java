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
package org.apache.camel.component.ai.resource;

import java.util.Arrays;

import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that registers a Camel route as an AI resource in the {@link AiResourceRegistry} on start and deregisters on
 * stop.
 *
 * @since 4.23
 */
public class AiResourceConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AiResourceConsumer.class);

    private final String resourceName;
    private final AiResourceConfiguration configuration;
    private AiResourceSpec registeredSpec;
    private String[] registeredTags;
    private boolean registeredInDefaultPool;

    public AiResourceConsumer(AiResourceEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.resourceName = endpoint.getResourceName();
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String resourceUri = configuration.getResourceUri();
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new IllegalArgumentException(
                    "The resourceUri option is required: ai-resource:" + resourceName + "?resourceUri=<uri>");
        }

        String desc = configuration.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = resourceName;
        }

        String mimeType = configuration.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = AiResource.DEFAULT_MIME_TYPE;
        }

        registeredSpec = new AiResourceSpec(resourceName, resourceUri, desc, mimeType, configuration.getTitle(), this);

        String tags = configuration.getTags();
        String[] parsedTags = splitTags(tags);
        if (parsedTags.length > 0) {
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
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Registering resource '{}' with tag '{}'", registeredSpec.getUri(), tag);
                registry.put(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            LOG.debug("Registering resource '{}' in default pool (no tags)", registeredSpec.getUri());
            registry.putDefault(registeredSpec);
        }
    }

    private void deregister() {
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTags != null) {
            for (String tag : registeredTags) {
                LOG.debug("Removing resource '{}' from tag '{}'", registeredSpec.getUri(), tag);
                registry.remove(tag, registeredSpec);
            }
        } else if (registeredInDefaultPool) {
            LOG.debug("Removing resource '{}' from default pool", registeredSpec.getUri());
            registry.removeDefault(registeredSpec);
        }
    }

    private static String[] splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(tags.trim().split("\\s*,\\s*"))
                .filter(tag -> !tag.isEmpty())
                .toArray(String[]::new);
    }
}
