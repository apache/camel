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
package org.apache.camel.component.spring.ws.bean;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.component.spring.ws.type.EndpointMappingType;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.server.EndpointInvocationChain;
import org.springframework.ws.server.EndpointMapping;
import org.springframework.ws.server.endpoint.MessageEndpoint;
import org.springframework.ws.server.endpoint.mapping.AbstractEndpointMapping;
import org.springframework.ws.server.endpoint.support.PayloadRootUtils;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.server.SoapEndpointInvocationChain;
import org.springframework.ws.soap.server.SoapEndpointMapping;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.xml.xpath.XPathExpression;

/**
 * Spring {@link EndpointMapping} for mapping messages to corresponding Camel
 * endpoints. This class needs to be registered in the Spring
 * <tt>ApplicationContext</tt> when consuming messages using any of the
 * following URI schemes:
 * <p/>
 * <ul>
 * <li><tt>spring-ws:rootqname:</tt><br/>
 * Equivalent to endpoint mappings specified through
 * {@link org.springframework.ws.server.endpoint.mapping.PayloadRootQNameEndpointMapping}
 * <p/>
 * <li><tt>spring-ws:soapaction:</tt><br/>
 * Equivalent to endpoint mappings specified through
 * {@link org.springframework.ws.soap.server.endpoint.mapping.SoapActionEndpointMapping}
 * <p/>
 * <li><tt>spring-ws:uri:</tt><br/>
 * Equivalent to endpoint mappings specified through
 * {@link org.springframework.ws.server.endpoint.mapping.UriEndpointMapping}
 * <p/>
 * <li><tt>spring-ws:xpathresult:</tt><br/>
 * Equivalent to endpoint mappings specified through
 * {@link org.springframework.ws.server.endpoint.mapping.XPathPayloadEndpointMapping}
 * </ul>
 *
 * @see org.springframework.ws.server.endpoint.mapping.AbstractEndpointMapping
 * @see org.springframework.ws.server.endpoint.mapping.PayloadRootQNameEndpointMapping
 * @see org.springframework.ws.server.endpoint.mapping.UriEndpointMapping
 * @see org.springframework.ws.server.endpoint.mapping.XPathPayloadEndpointMapping
 * @see org.springframework.ws.soap.server.endpoint.mapping.SoapActionEndpointMapping
 */
public class CamelEndpointMapping extends AbstractEndpointMapping implements InitializingBean, CamelSpringWSEndpointMapping, SoapEndpointMapping, CamelContextAware {

    private static final String DOUBLE_QUOTE = "\"";
    private static final String URI_PATH_WILDCARD = "*";
    private Map<EndpointMappingKey, MessageEndpoint> endpoints = new ConcurrentHashMap<>();
    private TransformerFactory transformerFactory;
    private CamelContext camelContext;

    private String[] actorsOrRoles;

    private boolean isUltimateReceiver = true;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected Object getEndpointInternal(MessageContext messageContext) throws Exception {
        for (EndpointMappingKey key : endpoints.keySet()) {
            String messageKey;
            switch (key.getType()) {
                case ROOT_QNAME:
                    messageKey = getRootQName(messageContext);
                    break;
                case SOAP_ACTION:
                    messageKey = getSoapAction(messageContext);
                    break;
                case XPATHRESULT:
                    messageKey = getXPathResult(messageContext, key.getExpression());
                    break;
                case URI:
                    messageKey = getUri();
                    break;
                case URI_PATH:
                    messageKey = getUriPath();

                    if (messageKey != null && key.getLookupKey().endsWith(URI_PATH_WILDCARD)) {
                        String lookupKey = key.getLookupKey().substring(0, key.getLookupKey().length() - 1);

                        if (messageKey.startsWith(lookupKey)) {
                            return endpoints.get(key);
                        }
                    }

                    break;
                default:
                    throw new RuntimeCamelException("Invalid mapping type specified. Supported types are: root QName, SOAP action, XPath expression and URI");
            }
            if (key.getLookupKey().equals(messageKey)) {
                return endpoints.get(key);
            }
        }
        return null;
    }

    @Override
    protected final EndpointInvocationChain createEndpointInvocationChain(MessageContext messageContext, Object endpoint, EndpointInterceptor[] interceptors) {
        for (EndpointMappingKey key : endpoints.keySet()) {
            if (EndpointMappingType.SOAP_ACTION.equals(key.getType())) {
                Object messageKey = getSoapAction(messageContext);
                if (key.getLookupKey().equals(messageKey)) {
                    return new SoapEndpointInvocationChain(endpoint, interceptors, actorsOrRoles, isUltimateReceiver);
                }
            }
        }
        return super.createEndpointInvocationChain(messageContext, endpoint, interceptors);
    }

    private String getSoapAction(MessageContext messageContext) {
        if (messageContext.getRequest() instanceof SoapMessage) {
            SoapMessage request = (SoapMessage)messageContext.getRequest();
            String soapAction = request.getSoapAction();
            if (StringUtils.hasLength(soapAction) && soapAction.startsWith(DOUBLE_QUOTE) && soapAction.endsWith(DOUBLE_QUOTE)) {
                return soapAction.substring(1, soapAction.length() - 1);
            }
            return soapAction;
        }
        return null;
    }

    private String getUri() throws URISyntaxException {
        WebServiceConnection webServiceConnection = getWeServiceConnection();
        if (webServiceConnection != null) {
            return webServiceConnection.getUri().toString();
        }
        return null;
    }

    private String getUriPath() throws URISyntaxException {
        WebServiceConnection webServiceConnection = getWeServiceConnection();
        if (webServiceConnection != null) {
            return webServiceConnection.getUri().getPath();
        }

        return null;
    }

    private WebServiceConnection getWeServiceConnection() {
        TransportContext transportContext = TransportContextHolder.getTransportContext();
        if (transportContext != null) {
            WebServiceConnection webServiceConnection = transportContext.getConnection();
            if (webServiceConnection != null) {
                return webServiceConnection;
            }
        }
        return null;
    }

    private String getRootQName(MessageContext messageContext) throws TransformerException {
        QName qName = PayloadRootUtils.getPayloadRootQName(messageContext.getRequest().getPayloadSource(), transformerFactory);
        return qName != null ? qName.toString() : null;
    }

    private String getXPathResult(MessageContext messageContext, XPathExpression expression) {
        if (expression != null) {
            Node domNode = camelContext.getTypeConverter().convertTo(Node.class, messageContext.getRequest().getPayloadSource());
            if (domNode != null) {
                return expression.evaluateAsString(domNode.getFirstChild());
            }
        }
        return null;
    }

    /**
     * Used by Camel Spring Web Services endpoint to register consumers
     */
    @Override
    public void addConsumer(EndpointMappingKey key, MessageEndpoint endpoint) {
        endpoints.put(key, endpoint);
    }

    /**
     * Used by Camel Spring Web Services endpoint to unregister consumers
     */
    @Override
    public void removeConsumer(Object key) {
        endpoints.remove(key);
    }

    /**
     * Gets the configured TransformerFactory
     */
    public TransformerFactory getTransformerFactory() {
        return transformerFactory;
    }

    /**
     * Optional setter to override default TransformerFactory
     */
    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (transformerFactory == null) {
            transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        }
    }

    @Override
    public final void setActorOrRole(String actorOrRole) {
        Assert.notNull(actorOrRole, "actorOrRole must not be null");
        actorsOrRoles = new String[] {actorOrRole};
    }

    @Override
    public final void setActorsOrRoles(String[] actorsOrRoles) {
        Assert.notEmpty(actorsOrRoles, "actorsOrRoles must not be empty");
        this.actorsOrRoles = actorsOrRoles;
    }

    @Override
    public final void setUltimateReceiver(boolean ultimateReceiver) {
        this.isUltimateReceiver = ultimateReceiver;
    }

}
