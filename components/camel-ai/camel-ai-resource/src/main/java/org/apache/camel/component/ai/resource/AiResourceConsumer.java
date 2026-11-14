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
import org.apache.camel.support.CamelContextHelper;
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
    private AiResourceTemplateSpec registeredTemplateSpec;
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
        if (registeredSpec == null && registeredTemplateSpec == null) {
            prepare();
        }
        register();
    }

    /**
     * Registers during route warm-up, which Camel completes for all routes before it starts any route consumer, so a
     * route that sends a request as soon as its consumer starts (such as {@code stream:in}) sees every resource. Routes
     * that are not started automatically are registered when their consumer starts.
     */
    void registerEarly() throws Exception {
        if (registeredSpec == null && registeredTemplateSpec == null
                && getRoute() != null && CamelContextHelper.isAutoStartup(getRoute())) {
            prepare();
            register();
        }
    }

    /**
     * Removes an early registration whose consumer never started, e.g. when another route failed to start.
     */
    void deregisterEarly() {
        if ((registeredSpec != null || registeredTemplateSpec != null) && !isStarted()) {
            deregister();
            registeredSpec = null;
            registeredTemplateSpec = null;
        }
    }

    private void prepare() {
        String resourceUri = configuration.getResourceUri();
        String uriTemplate = configuration.getUriTemplate();
        boolean hasResourceUri = resourceUri != null && !resourceUri.isBlank();
        boolean hasUriTemplate = uriTemplate != null && !uriTemplate.isBlank();
        if (hasResourceUri == hasUriTemplate) {
            if (hasResourceUri) {
                throw new IllegalArgumentException(
                        "Only one of resourceUri or uriTemplate can be configured on ai-resource:" + resourceName);
            }
            throw new IllegalArgumentException(
                    "The resourceUri or uriTemplate option is required: ai-resource:" + resourceName
                                               + "?resourceUri=<uri> or ?uriTemplate=<uri-template>");
        }

        String desc = configuration.getDescription();
        if (desc == null || desc.isBlank()) {
            desc = resourceName;
        }

        String mimeType = configuration.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = AiResource.DEFAULT_MIME_TYPE;
        }

        if (hasResourceUri) {
            // The MCP SDK treats braces in fixed resource URIs as URI-template expressions.
            // Require route authors to configure uriTemplate explicitly instead.
            if (resourceUri.indexOf('{') >= 0 || resourceUri.indexOf('}') >= 0) {
                throw new IllegalArgumentException(
                        "Fixed resourceUri must not contain '{' or '}'; use uriTemplate instead: ai-resource:"
                                                   + resourceName);
            }
            registeredSpec = new AiResourceSpec(
                    resourceName, resourceUri, desc, mimeType, configuration.getTitle(), this);
        } else {
            registeredTemplateSpec = new AiResourceTemplateSpec(
                    resourceName, uriTemplate, desc, mimeType, configuration.getTitle(), this);
        }

        String tags = configuration.getTags();
        String[] parsedTags = splitTags(tags);
        if (parsedTags.length > 0) {
            registeredTags = parsedTags;
            registeredInDefaultPool = false;
        } else {
            registeredTags = null;
            registeredInDefaultPool = true;
        }
    }

    @Override
    protected void doSuspend() throws Exception {
        if (registeredSpec != null || registeredTemplateSpec != null) {
            deregister();
        }
        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();
        if (registeredSpec != null || registeredTemplateSpec != null) {
            register();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (registeredSpec != null || registeredTemplateSpec != null) {
            deregister();
            registeredSpec = null;
            registeredTemplateSpec = null;
            registeredTags = null;
            registeredInDefaultPool = false;
        }
        super.doStop();
    }

    private void register() {
        AiResourceRegistry registry = AiResourceRegistry.getOrCreate(getEndpoint().getCamelContext());
        if (registeredTemplateSpec != null) {
            if (registeredTags != null) {
                for (String tag : registeredTags) {
                    LOG.debug("Registering resource template '{}' with tag '{}'",
                            registeredTemplateSpec.getUriTemplate(), tag);
                    registry.putTemplate(tag, registeredTemplateSpec);
                }
            } else if (registeredInDefaultPool) {
                LOG.debug("Registering resource template '{}' in default pool",
                        registeredTemplateSpec.getUriTemplate());
                registry.putDefaultTemplate(registeredTemplateSpec);
            }
            return;
        }
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
        if (registeredTemplateSpec != null) {
            if (registeredTags != null) {
                for (String tag : registeredTags) {
                    LOG.debug("Removing resource template '{}' from tag '{}'",
                            registeredTemplateSpec.getUriTemplate(), tag);
                    registry.removeTemplate(tag, registeredTemplateSpec);
                }
            } else if (registeredInDefaultPool) {
                LOG.debug("Removing resource template '{}' from default pool",
                        registeredTemplateSpec.getUriTemplate());
                registry.removeDefaultTemplate(registeredTemplateSpec);
            }
            return;
        }
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
