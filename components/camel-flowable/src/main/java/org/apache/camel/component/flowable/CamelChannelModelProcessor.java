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
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.eventregistry.api.ChannelModelProcessor;
import org.flowable.eventregistry.api.EventRegistry;
import org.flowable.eventregistry.api.EventRepositoryService;
import org.flowable.eventregistry.api.OutboundEventChannelAdapter;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.eventregistry.impl.util.CommandContextUtil;
import org.flowable.eventregistry.model.CamelInboundChannelModel;
import org.flowable.eventregistry.model.CamelOutboundChannelModel;
import org.flowable.eventregistry.model.ChannelModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelChannelModelProcessor implements ChannelModelProcessor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected CamelContext camelContext;

    public CamelChannelModelProcessor(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean canProcess(ChannelModel channelModel) {
        return channelModel instanceof CamelInboundChannelModel || channelModel instanceof CamelOutboundChannelModel;
    }

    @Override
    public boolean canProcessIfChannelModelAlreadyRegistered(ChannelModel channelModel) {
        return channelModel instanceof CamelOutboundChannelModel;
    }

    @Override
    public void registerChannelModel(
            ChannelModel channelModel, String tenantId, EventRegistry eventRegistry,
            EventRepositoryService eventRepositoryService,
            boolean fallbackToDefaultTenant) {

        if (channelModel instanceof CamelInboundChannelModel camelInboundChannelModel) {
            logger.debug("Starting to register inbound channel {} in tenant {}", channelModel.getKey(), tenantId);
            processInboundDefinition(camelInboundChannelModel, tenantId);
            logger.debug("Finished registering inbound channel {} in tenant {}", channelModel.getKey(), tenantId);

        } else if (channelModel instanceof CamelOutboundChannelModel camelOutboundChannelModel) {
            logger.debug("Starting to register outbound channel {} in tenant {}", channelModel.getKey(), tenantId);
            processOutboundDefinition(camelOutboundChannelModel, tenantId);
            logger.debug("Finished registering outbound channel {} in tenant {}", channelModel.getKey(), tenantId);
        }
    }

    protected void processInboundDefinition(CamelInboundChannelModel channelModel, String tenantId) {
        EventRegistryEngineConfiguration eventRegistryEngineConfiguration = CommandContextUtil.getEventRegistryConfiguration();
        try (FlowableEndpoint endpoint = new FlowableEndpoint(channelModel, eventRegistryEngineConfiguration)) {

            camelContext.addEndpoint(endpoint.getEndpointUri(), endpoint);
            if (StringUtils.isNotEmpty(channelModel.getSourceUri())) {
                camelContext.addRoutes(new RouteBuilder() {

                    @Override
                    public void configure() throws Exception {
                        from(channelModel.getSourceUri()).routeId(channelModel.getKey()).to(endpoint.getEndpointUri());
                    }
                });
            }

        } catch (Exception e) {
            throw new FlowableException(
                    "Error creating producer for inbound channel " + channelModel.getKey() + " in tenant " + tenantId);
        }
    }

    protected void processOutboundDefinition(CamelOutboundChannelModel channelModel, String tenantId) {
        String destination = channelModel.getDestination();
        if (channelModel.getOutboundEventChannelAdapter() == null && StringUtils.isNotEmpty(destination)) {
            channelModel.setOutboundEventChannelAdapter(createOutboundEventChannelAdapter(channelModel, tenantId));
        }
    }

    protected OutboundEventChannelAdapter<String> createOutboundEventChannelAdapter(
            CamelOutboundChannelModel channelModel, String tenantId) {

        EventRegistryEngineConfiguration eventRegistryEngineConfiguration = CommandContextUtil.getEventRegistryConfiguration();
        String destination = resolve(channelModel.getDestination());
        try (FlowableEndpoint endpoint = new FlowableEndpoint(channelModel, eventRegistryEngineConfiguration)) {
            camelContext.addEndpoint(endpoint.getEndpointUri(), endpoint);
            camelContext.addRoutes(new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from(endpoint).routeId(channelModel.getKey()).to(destination);
                }
            });

            return new CamelOperationsOutboundEventChannelAdapter(endpoint);

        } catch (Exception e) {
            throw new FlowableException(
                    "Error creating route for outbound channel " + channelModel.getKey() + " in tenant " + tenantId);
        }
    }

    @Override
    public void unregisterChannelModel(
            ChannelModel channelModel, String tenantId, EventRepositoryService eventRepositoryService) {

    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    protected String resolve(String value) {
        return value;
    }
}
