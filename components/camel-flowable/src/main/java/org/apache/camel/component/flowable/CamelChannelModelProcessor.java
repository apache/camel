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
import org.flowable.eventregistry.model.CamelInboundChannelModel;
import org.flowable.eventregistry.model.CamelOutboundChannelModel;
import org.flowable.eventregistry.model.ChannelModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.StringValueResolver;

public class CamelChannelModelProcessor
        implements BeanFactoryAware, ApplicationContextAware, ApplicationListener<ContextRefreshedEvent>,
        ChannelModelProcessor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected CamelContext camelContext;

    protected BeanFactory beanFactory;
    protected ApplicationContext applicationContext;
    protected boolean contextRefreshed;

    protected StringValueResolver embeddedValueResolver;

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

        if (channelModel instanceof CamelInboundChannelModel) {
            CamelInboundChannelModel camelInboundChannelModel = (CamelInboundChannelModel) channelModel;
            logger.info("Starting to register inbound channel {} in tenant {}", channelModel.getKey(), tenantId);
            processInboundDefinition(camelInboundChannelModel, tenantId);
            logger.info("Finished registering inbound channel {} in tenant {}", channelModel.getKey(), tenantId);

        } else if (channelModel instanceof CamelOutboundChannelModel) {
            logger.info("Starting to register outbound channel {} in tenant {}", channelModel.getKey(), tenantId);
            processOutboundDefinition((CamelOutboundChannelModel) channelModel, tenantId);
            logger.info("Finished registering outbound channel {} in tenant {}", channelModel.getKey(), tenantId);
        }
    }

    protected void processInboundDefinition(CamelInboundChannelModel channelModel, String tenantId) {
        try (FlowableEndpoint endpoint
                = new FlowableEndpoint(channelModel, getEventRegistryEngineConfiguration(), camelContext)) {

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
            logger.error("Error creating producer for inbound channel {} in tenant {}", channelModel.getKey(), tenantId);
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

        String destination = resolve(channelModel.getDestination());
        try (FlowableEndpoint endpoint
                = new FlowableEndpoint(channelModel, getEventRegistryEngineConfiguration(), camelContext)) {
            camelContext.addEndpoint(endpoint.getEndpointUri(), endpoint);
            camelContext.addRoutes(new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from(endpoint).routeId(channelModel.getKey()).to(destination);
                }
            });

            return new CamelOperationsOutboundEventChannelAdapter(endpoint);

        } catch (Exception e) {
            logger.error("Error creating route for outbound channel {} in tenant {}", channelModel.getKey(), tenantId);
            throw new FlowableException(
                    "Error creating route for outbound channel " + channelModel.getKey() + " in tenant " + tenantId);
        }
    }

    @Override
    public void unregisterChannelModel(
            ChannelModel channelModel, String tenantId, EventRepositoryService eventRepositoryService) {

    }

    protected String resolve(String value) {
        if (embeddedValueResolver != null) {
            return embeddedValueResolver.resolveStringValue(value);
        } else {
            return value;
        }
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        if (beanFactory instanceof ConfigurableBeanFactory) {
            this.embeddedValueResolver = new EmbeddedValueResolver((ConfigurableBeanFactory) beanFactory);
        }
    }

    protected EventRegistryEngineConfiguration getEventRegistryEngineConfiguration() {
        return applicationContext.getBean(EventRegistryEngineConfiguration.class);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == this.applicationContext) {
            this.contextRefreshed = true;
        }
    }
}
