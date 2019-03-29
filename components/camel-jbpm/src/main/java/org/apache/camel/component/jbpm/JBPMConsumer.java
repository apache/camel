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
package org.apache.camel.component.jbpm;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.jbpm.emitters.CamelEventEmitter;
import org.apache.camel.component.jbpm.listeners.CamelCaseEventListener;
import org.apache.camel.component.jbpm.listeners.CamelProcessEventListener;
import org.apache.camel.component.jbpm.listeners.CamelTaskEventListener;
import org.apache.camel.support.DefaultConsumer;
import org.jbpm.services.api.DeploymentEvent;
import org.jbpm.services.api.DeploymentEventListener;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ListenerSupport;
import org.jbpm.services.api.model.DeployedUnit;
import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.internal.runtime.manager.CacheManager;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JBPMConsumer extends DefaultConsumer implements DeploymentEventListener {
    
    private static final transient Logger LOGGER = LoggerFactory.getLogger(JBPMConsumer.class);
   
    private JBPMEndpoint endpoint;
    private JBPMConfiguration configuration;
    
    public JBPMConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        
        this.endpoint = (JBPMEndpoint) endpoint;
        this.configuration = ((JBPMEndpoint) getEndpoint()).getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        
        DeploymentService deploymentService = (DeploymentService) ServiceRegistry.get().service(ServiceRegistry.DEPLOYMENT_SERVICE);        
        
        if (configuration.getDeploymentId() != null) {
            InternalRuntimeManager manager = (InternalRuntimeManager) deploymentService.getRuntimeManager(configuration.getDeploymentId());
            configure(manager, this);
            
            LOGGER.debug("JBPM Camel Consumer configured and started for deployment id {}", configuration.getDeploymentId());
        } else {
            
            ((ListenerSupport) deploymentService).addListener(this);
            
            for (DeployedUnit deployed : deploymentService.getDeployedUnits()) {
                InternalRuntimeManager manager = (InternalRuntimeManager) deployed.getRuntimeManager();
                configure(manager, this); 
            }
            
            LOGGER.debug("JBPM Camel Consumer configured and started on all available deployments");
        }
        
        
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        DeploymentService deploymentService = (DeploymentService) ServiceRegistry.get().service(ServiceRegistry.DEPLOYMENT_SERVICE);        
        if (configuration.getDeploymentId() != null) {
            LOGGER.debug("JBPM Camel Consumer unconfigured and stopped for deployment id {}", configuration.getDeploymentId());
        } else {
            ((ListenerSupport) deploymentService).removeListener(this);
            
            LOGGER.debug("JBPM Camel Consumer unconfigured and stopped on all available deployments");
        }
        
        if (JBPMConstants.JBPM_EVENT_EMITTER.equals(configuration.getEventListenerType())) {
            ServiceRegistry.get().remove("CamelEventEmitter");
        }
        
    }

    public void sendMessage(String eventType, Object body) {
        Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setHeader("EventType", eventType);
        
        exchange.getIn().setBody(body);

        if (!endpoint.isSynchronous()) {
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    // handle any thrown exception
                    if (exchange.getException() != null) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                    }
                }
            });
        } else {
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // handle any thrown exception
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
    
    @Override
    public void onDeploy(DeploymentEvent event) {
        InternalRuntimeManager manager = (InternalRuntimeManager) event.getDeployedUnit().getRuntimeManager();
        configure(manager, this);       

    }

    @Override
    public void onUnDeploy(DeploymentEvent event) {  
        // no-op
    }

    @Override
    public void onActivate(DeploymentEvent event) {
        // no-op
        
    }

    @Override
    public void onDeactivate(DeploymentEvent event) {
        // no-op
        
    }

    
    protected void configure(InternalRuntimeManager manager, JBPMConsumer consumer) {
        String eventListenerType = configuration.getEventListenerType();
        if (eventListenerType == null) {
            return;
        }
        
       
        configureConsumer(eventListenerType, manager, consumer);
        
    }
    
    protected void configureConsumer(String eventListenerType, InternalRuntimeManager manager, JBPMConsumer consumer) {
        LOGGER.debug("Configuring Camel JBPM Consumer for {} on runtime manager {}", eventListenerType, manager);
       
        CacheManager cacheManager = manager.getCacheManager();
        JBPMCamelConsumerAware consumerAware = null;
        if (JBPMConstants.JBPM_PROCESS_EVENT_LISTENER.equals(eventListenerType)) {
            consumerAware = (JBPMCamelConsumerAware) cacheManager.get("new org.apache.camel.component.jbpm.listeners.CamelProcessEventListener()");
            if (consumerAware == null) {
                consumerAware = new CamelProcessEventListener();
                cacheManager.add("new org.apache.camel.component.jbpm.listeners.CamelProcessEventListener()", consumerAware);
            }
            LOGGER.debug("Configuring JBPMConsumer on process event listener {}", consumerAware);
        } else if (JBPMConstants.JBPM_TASK_EVENT_LISTENER.equals(eventListenerType)) {
            consumerAware = (JBPMCamelConsumerAware) cacheManager.get("new org.apache.camel.component.jbpm.listeners.CamelTaskEventListener()");
            if (consumerAware == null) {
                consumerAware = new CamelTaskEventListener();
                cacheManager.add("new org.apache.camel.component.jbpm.listeners.CamelTaskEventListener()", consumerAware);
            }
            LOGGER.debug("Configuring JBPMConsumer on task event listener {}", consumerAware);
        } else if (JBPMConstants.JBPM_CASE_EVENT_LISTENER.equals(eventListenerType)) {
            consumerAware = (JBPMCamelConsumerAware) cacheManager.get("new org.apache.camel.component.jbpm.listeners.CamelCaseEventListener()");
            if (consumerAware == null) {
                consumerAware = new CamelCaseEventListener();
                cacheManager.add("new org.apache.camel.component.jbpm.listeners.CamelCaseEventListener()", consumerAware);
            }
            LOGGER.debug("Configuring JBPMConsumer on case event listener {}", consumerAware);
        } else if (JBPMConstants.JBPM_EVENT_EMITTER.equals(eventListenerType)) {
            LOGGER.debug("Configuring JBPMConsumer for event emitter");
            ServiceRegistry.get().register("CamelEventEmitter", new CamelEventEmitter(this, configuration.getEmitterSendItems()));
            
            return;
        }        
  
        LOGGER.debug("Adding consumer {} on {}", consumer, consumerAware);
        consumerAware.addConsumer(consumer);    
        
    }

    @Override
    public String toString() {
        return "JBPMConsumer [endpoint=" + endpoint + ", configuration=" + configuration + "]";
    }
}
