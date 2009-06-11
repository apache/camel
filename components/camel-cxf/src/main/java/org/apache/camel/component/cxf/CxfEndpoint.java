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
package org.apache.camel.component.cxf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.headers.DefaultMessageHeadersRelay;
import org.apache.camel.component.cxf.headers.MessageHeadersRelay;
import org.apache.camel.component.cxf.headers.SoapMessageHeadersRelay;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;


/**
 * Defines the <a href="http://activemq.apache.org/camel/cxf.html">CXF Endpoint</a>
 *
 * @version $Revision$
 */
public class CxfEndpoint extends DefaultEndpoint<CxfExchange> {
    private final CxfComponent component;
    private final String address;
    private String wsdlURL;
    private String serviceClass;
    private String portName;
    private String serviceName;
    private String dataFormat;
    private String beanId;
    private String serviceClassInstance;
    private boolean isWrapped;
    private boolean isSpringContextEndpoint;
    private boolean inOut = true;
    private boolean relayHeaders = true;

    private Boolean isSetDefaultBus;
    private ConfigurerImpl configurer;
    private CxfEndpointBean cxfEndpointBean;
 
    private Map<String, MessageHeadersRelay> ns2Relay = new HashMap<String, MessageHeadersRelay>();


    public CxfEndpoint(String uri, String address, CxfComponent component) {
        super(uri, component);
        this.component = component;
        this.address = address;
        
        if (address.startsWith(CxfConstants.SPRING_CONTEXT_ENDPOINT)) {
            isSpringContextEndpoint = true;
            // Get the bean from the Spring context
            beanId = address.substring(CxfConstants.SPRING_CONTEXT_ENDPOINT.length());
            if (beanId.startsWith("//")) {
                beanId = beanId.substring(2);
            }
            SpringCamelContext context = (SpringCamelContext) this.getCamelContext();            
            configurer = new ConfigurerImpl(context.getApplicationContext());
            cxfEndpointBean = (CxfEndpointBean) context.getApplicationContext().getBean(beanId);
            ObjectHelper.notNull(cxfEndpointBean, "cxfEndpointBean");
        }
        
        initializeHeadersRelaysMap();
    }

    public Producer<CxfExchange> createProducer() throws Exception {
        return new CxfProducer(this);
    }

    public Consumer<CxfExchange> createConsumer(Processor processor) throws Exception {
        return new CxfConsumer(this, processor);
    }

    public CxfExchange createExchange() {
        return new CxfExchange(getCamelContext(), getExchangePattern());
    }

    public CxfExchange createExchange(ExchangePattern pattern) {
        return new CxfExchange(getCamelContext(), pattern);
    }

    public CxfExchange createExchange(Message inMessage) {
        return new CxfExchange(getCamelContext(), getExchangePattern(), inMessage);
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(String format) {
        dataFormat = format;
    }

    public boolean isSpringContextEndpoint() {
        return isSpringContextEndpoint;
    }

    public String getAddress() {
        return address;
    }

    public String getWsdlURL() {
        return wsdlURL;
    }

    public void setWsdlURL(String url) {
        wsdlURL = url;
    }

    public void setSetDefaultBus(Boolean set) {
        isSetDefaultBus = set;
    }

    public Boolean isSetDefaultBus() {
        return isSetDefaultBus;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(String className) {        
        serviceClass = className;
    }
    
    public String getServiceClassInstance() {
        return serviceClassInstance;
    }
    
    public void setServiceClassInstance(String classInstance) {
        serviceClassInstance = classInstance;
    }

    public void setPortName(String port) {
        portName = port;
    }

    public void setServiceName(String service) {
        serviceName = service;
    }

    public String getPortName() {
        return portName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public boolean isInOut() {
        return inOut;
    }

    public void setInOut(boolean inOut) {
        this.inOut = inOut;
    }

    public boolean isRelayHeaders() {
        return relayHeaders;
    }

    public void setRelayHeaders(boolean relayHeaders) {
        this.relayHeaders = relayHeaders;
    }

    public boolean isWrapped() {
        return isWrapped;
    }

    public void setWrapped(boolean wrapped) {
        isWrapped = wrapped;
    }


    public CxfComponent getComponent() {
        return component;
    }

    public boolean isSingleton() {
        return true;
    }

    public String getBeanId() {
        return beanId;
    }
    
    public CxfEndpointBean getCxfEndpointBean() {
        return cxfEndpointBean;
    }

    public void configure(Object beanInstance) {
        // check the ApplicationContext states first , and call the refresh if necessary
        if (((SpringCamelContext)getCamelContext()).getApplicationContext() instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext context = (ConfigurableApplicationContext)((SpringCamelContext)getCamelContext()).getApplicationContext();
            if (!context.isActive()) {
                context.refresh();
            }
        }
        configurer.configureBean(beanId, beanInstance);
    }

    public ApplicationContext getApplicationContext() {
        if (getCamelContext() instanceof SpringCamelContext) {
            SpringCamelContext context = (SpringCamelContext) getCamelContext();
            return context.getApplicationContext();
        } else {
            return null;
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return component.getHeaderFilterStrategy();
    }

    // adds new relays and makes sure that none of newly added relays 
    // have a matching activation namespace
    public void setMessageHeadersRelay(Collection<MessageHeadersRelay> relays) {
        Map<String, MessageHeadersRelay> localRelays = new HashMap<String, MessageHeadersRelay>();
        for (MessageHeadersRelay relay : relays) {
            setMessageHeadersRelay(relay, localRelays, false);
        }
        // once we verified that namespaces are represented by one and only relay
        // allow to replace old relays with new ones for a given namespace
        ns2Relay.putAll(localRelays);
    }

    public Collection<MessageHeadersRelay> getMessageHeadersRelays() {
        Collection<MessageHeadersRelay> relays = new ArrayList<MessageHeadersRelay>();
        for (MessageHeadersRelay relay : ns2Relay.values()) {
            if (!relays.contains(relay)) {
                relays.add(relay);
            }
        }
        return relays;
    }
    
    public MessageHeadersRelay getMessageHeadersRelay(String ns) {
        return ns2Relay.get(ns);
    }

    protected void initializeHeadersRelaysMap() {
        Collection<MessageHeadersRelay> defaultRelays = new ArrayList<MessageHeadersRelay>(); 
        defaultRelays.addAll(Arrays.asList(new DefaultMessageHeadersRelay(), new SoapMessageHeadersRelay()));
        
        setMessageHeadersRelay(defaultRelays);
        
        if (cxfEndpointBean == null || cxfEndpointBean.getProperties() == null) {
            return;
        }
        Object v = cxfEndpointBean.getProperties().get(CxfConstants.CAMEL_CXF_MESSAGE_HEADER_RELAYS);
        if (v == null || !(v instanceof Collection)) {
            return;
        }
        Collection<?> c = (Collection<?>)v;
        Collection<MessageHeadersRelay> relays = null;
        try {
            relays = CastUtils.cast((Collection<?>)c);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("The property " 
                                               + CxfConstants.CAMEL_CXF_MESSAGE_HEADER_RELAYS
                                               + " must have a list that consists of classes inherited from " 
                                               + MessageHeadersRelay.class.getName());
        }
        setMessageHeadersRelay(relays);    
    }

    private static void setMessageHeadersRelay(MessageHeadersRelay relay, 
                                               Map<String, MessageHeadersRelay> ns2Relay,
                                               boolean allowClash) {
        for (String ns : relay.getActivationNamespaces()) {
            if (ns2Relay.containsKey(ns) && ns2Relay.get(ns) != relay && !allowClash) {
                throw new IllegalArgumentException("More then one MessageHeaderRelay activates "
                                                   + "for the same namespace: " + ns);
            }
            ns2Relay.put(ns, relay);
        }
    }
}
