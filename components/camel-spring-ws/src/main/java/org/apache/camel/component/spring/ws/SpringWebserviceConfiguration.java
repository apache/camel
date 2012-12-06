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
package org.apache.camel.component.spring.ws;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher;
import org.apache.camel.component.spring.ws.bean.CamelSpringWSEndpointMapping;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.apache.camel.component.spring.ws.filter.impl.BasicMessageFilter;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.springframework.util.StringUtils;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.addressing.server.annotation.Action;

public class SpringWebserviceConfiguration {

    /* Producer configuration */
    private WebServiceTemplate webServiceTemplate;
    private String soapAction;
    private URI wsAddressingAction;
    private URI outputAction;
    private URI faultAction;
    private URI faultTo;
    private URI replyTo;
    private int timeout = -1;

    /* Consumer configuration */
    private CamelSpringWSEndpointMapping endpointMapping;
    private CamelEndpointDispatcher endpointDispatcher;
    private EndpointMappingKey endpointMappingKey;
    private SSLContextParameters sslContextParameters;

    private XmlConverter xmlConverter;
    private MessageFilter messageFilter;

    public WebServiceTemplate getWebServiceTemplate() {
        return webServiceTemplate;
    }

    public void setWebServiceTemplate(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }

    public String getEndpointUri() {
        if (endpointMappingKey != null) {
            // only for consumers, use lookup key as endpoint uri/key
            return encode(endpointMappingKey.getLookupKey());
        } else if (webServiceTemplate != null) {
            return webServiceTemplate.getDefaultUri();
        }
        return null;
    }

    public URI getWsAddressingAction() {
        return wsAddressingAction;
    }

    public void setWsAddressingAction(URI wsAddressingAction) {
        this.wsAddressingAction = wsAddressingAction;
    }

    public void setWsAddressingAction(String wsAddressingAction) throws URISyntaxException {
        if (StringUtils.hasText(wsAddressingAction)) {
            setWsAddressingAction(new URI(wsAddressingAction));
        }
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public CamelSpringWSEndpointMapping getEndpointMapping() {
        return endpointMapping;
    }

    public void setEndpointMapping(CamelSpringWSEndpointMapping endpointMapping) {
        this.endpointMapping = endpointMapping;
    }

    public EndpointMappingKey getEndpointMappingKey() {
        return endpointMappingKey;
    }

    public void setEndpointMappingKey(EndpointMappingKey endpointMappingKey) {
        this.endpointMappingKey = endpointMappingKey;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public CamelEndpointDispatcher getEndpointDispatcher() {
        return endpointDispatcher;
    }

    public void setEndpointDispatcher(CamelEndpointDispatcher endpointDispatcher) {
        this.endpointDispatcher = endpointDispatcher;
    }

    public XmlConverter getXmlConverter() {
        return xmlConverter;
    }

    public void setXmlConverter(XmlConverter xmlConverter) {
        this.xmlConverter = xmlConverter;
    }

    public static String encode(String uri) {
        int i = uri.lastIndexOf('}');
        return i == -1 ? uri : (uri.subSequence(0, i) + ")" + uri.substring(i + 1)).replaceFirst("\\{", "(");
    }

    public static String decode(String uri) {
        int i = uri.lastIndexOf(')');
        return i == -1 ? uri : (uri.subSequence(0, i) + "}" + uri.substring(i + 1)).replaceFirst("\\(", "{");
    }

    /**
     * Default setter to override failsafe message filter.
     * 
     * @param messageFilter non-default MessageFilter
     */
    public void setMessageFilter(MessageFilter messageFilter) {
        this.messageFilter = messageFilter;

    }

    /**
     * Gets the configured MessageFilter.
     * 
     * Note: The only place that sets fail safe strategy.
     * 
     * @return instance of MessageFilter that is never null;
     */
    public MessageFilter getMessageFilter() {
        if (this.messageFilter == null) {
            this.messageFilter = new BasicMessageFilter();
        }
        return this.messageFilter;
    }

    /**
     * Signifies the value for the response WS-Addressing <code>Action</code>
     * header that is provided by the method.
     * 
     * @see {@link Action}
     */
    public URI getOutputAction() {
        return outputAction;
    }

    public void setOutputAction(String output) throws URISyntaxException {
        if (StringUtils.hasText(output)) {
            setOutputAction(new URI(output));
        }
    }

    public void setOutputAction(URI outputAction) {
        this.outputAction = outputAction;
    }

    /**
     * Signifies the value for the faultAction response WS-Addressing
     * <code>Fault Action</code> header that is provided by the method.
     * 
     * @see {@link Action}
     */
    public URI getFaultAction() {
        return faultAction;
    }

    public void setFaultAction(String fault) throws URISyntaxException {
        if (StringUtils.hasText(fault)) {
            setFaultAction(new URI(fault));
        }
    }

    public void setFaultAction(URI fault) {
        this.faultAction = fault;
    }

    /**
     * Signifies the value for the faultAction response WS-Addressing
     * <code>FaultTo</code> header that is provided by the method.
     * 
     * @see {@link Action}
     */
    public URI getFaultTo() {
        return faultTo;
    }

    public void setFaultTo(String faultTo) throws URISyntaxException {
        if (StringUtils.hasText(faultTo)) {
            setFaultTo(new URI(faultTo));
        }
    }

    public void setFaultTo(URI faultTo) {
        this.faultTo = faultTo;
    }

    /**
     * Signifies the value for the replyTo response WS-Addressing
     * <code>ReplyTo</code> header that is provided by the method.
     * 
     * @see {@link Action}
     */
    public URI getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyToAction) throws URISyntaxException {
        if (StringUtils.hasText(replyToAction)) {
            setReplyTo(new URI(replyToAction));
        }
    }

    public void setReplyTo(URI replyToAction) {
        this.replyTo = replyToAction;
    }

}
