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
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.springframework.util.StringUtils;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.addressing.messageid.MessageIdStrategy;
import org.springframework.ws.soap.addressing.server.annotation.Action;
import org.springframework.ws.transport.WebServiceMessageSender;

@UriParams
public class SpringWebserviceConfiguration {

    private XmlConverter xmlConverter;

    @UriPath(label = "producer")
    private String webServiceEndpointUri;

    /* Common configuration */
    @UriParam
    private MessageFilter messageFilter;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    /* Producer configuration */
    @UriParam(label = "producer")
    private WebServiceTemplate webServiceTemplate;
    @UriParam(label = "producer")
    private WebServiceMessageSender messageSender;
    @UriParam(label = "producer")
    private WebServiceMessageFactory messageFactory;
    @UriParam(label = "producer")
    private String soapAction;
    @UriParam(label = "producer")
    private URI wsAddressingAction;
    @UriParam(label = "producer")
    private URI outputAction;
    @UriParam(label = "producer")
    private URI faultAction;
    @UriParam(label = "producer")
    private URI faultTo;
    @UriParam(label = "producer")
    private URI replyTo;
    @UriParam(label = "producer")
    private MessageIdStrategy messageIdStrategy;
    @UriParam(label = "producer")
    private int timeout = -1;
    @UriParam(label = "producer")
    private boolean allowResponseHeaderOverride;
    @UriParam(label = "producer")
    private boolean allowResponseAttachmentOverride;
    
    /* Consumer configuration */
    @UriParam(label = "consumer")
    private EndpointMappingKey endpointMappingKey;
    @UriParam(label = "consumer")
    private CamelSpringWSEndpointMapping endpointMapping;
    @UriParam(label = "consumer")
    private CamelEndpointDispatcher endpointDispatcher;
    @UriParam(label = "consumer")
    private String expression;

    public WebServiceTemplate getWebServiceTemplate() {
        return webServiceTemplate;
    }

    /**
     * Option to provide a custom WebServiceTemplate.
     * This allows for full control over client-side web services handling; like adding a custom interceptor
     * or specifying a fault resolver, message sender or message factory.
     */
    public void setWebServiceTemplate(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public WebServiceMessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * Option to provide a custom WebServiceMessageFactory. For example when you want Apache Axiom to handle web service messages instead of SAAJ.
     */
    public void setMessageFactory(WebServiceMessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public String getWebServiceEndpointUri() {
        return webServiceEndpointUri;
    }

    /**
     * The default Web Service endpoint uri to use for the producer.
     */
    public void setWebServiceEndpointUri(String webServiceEndpointUri) {
        this.webServiceEndpointUri = webServiceEndpointUri;
    }

    public String getExpression() {
        return expression;
    }

    /**
     * The XPath expression to use when option type=xpathresult. Then this option is required to be configured.
     */
    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getSoapAction() {
        return soapAction;
    }

    /**
     * SOAP action to include inside a SOAP request when accessing remote web services
     */
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

    /**
     * WS-Addressing 1.0 action header to include when accessing web services.
     * The To header is set to the address of the web service as specified in the endpoint URI (default Spring-WS behavior).
     */
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

    /**
     * Sets the socket read timeout (in milliseconds) while invoking a webservice using the producer,
     * see URLConnection.setReadTimeout() and CommonsHttpMessageSender.setReadTimeout().
     * This option works when using the built-in message sender implementations:
     * CommonsHttpMessageSender and HttpUrlConnectionMessageSender.
     * One of these implementations will be used by default for HTTP based services unless you customize the
     * Spring WS configuration options supplied to the component.
     * If you are using a non-standard sender, it is assumed that you will handle your own timeout configuration.
     * The built-in message sender HttpComponentsMessageSender is considered instead of CommonsHttpMessageSender
     * which has been deprecated, see HttpComponentsMessageSender.setReadTimeout().
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public CamelSpringWSEndpointMapping getEndpointMapping() {
        return endpointMapping;
    }

    /**
     * Reference to an instance of org.apache.camel.component.spring.ws.bean.CamelEndpointMapping in the Registry/ApplicationContext.
     * Only one bean is required in the registry to serve all Camel/Spring-WS endpoints.
     * This bean is auto-discovered by the MessageDispatcher and used to map requests to Camel endpoints based
     * on characteristics specified on the endpoint (like root QName, SOAP action, etc)
     */
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

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public CamelEndpointDispatcher getEndpointDispatcher() {
        return endpointDispatcher;
    }

    /**
     * Spring {@link org.springframework.ws.server.endpoint.MessageEndpoint} for dispatching messages received by Spring-WS to a Camel endpoint,
     * to integrate with existing (legacy) endpoint mappings like PayloadRootQNameEndpointMapping, SoapActionEndpointMapping, etc.
     */
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
     * Option to provide a custom MessageFilter. For example when you want to process your headers or attachments by your own.
     */
    public void setMessageFilter(MessageFilter messageFilter) {
        this.messageFilter = messageFilter;
    }

    public MessageFilter getMessageFilter() {
        return messageFilter;
    }

    public URI getOutputAction() {
        return outputAction;
    }

    /**
     * Signifies the value for the response WS-Addressing <code>Action</code>
     * header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setOutputAction(URI outputAction) {
        this.outputAction = outputAction;
    }

    public void setOutputAction(String output) throws URISyntaxException {
        if (StringUtils.hasText(output)) {
            setOutputAction(new URI(output));
        }
    }

    public URI getFaultAction() {
        return faultAction;
    }

    /**
     * Signifies the value for the faultAction response WS-Addressing
     * <code>Fault Action</code> header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setFaultAction(String fault) throws URISyntaxException {
        if (StringUtils.hasText(fault)) {
            setFaultAction(new URI(fault));
        }
    }

    /**
     * Signifies the value for the faultAction response WS-Addressing
     * <code>Fault Action</code> header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setFaultAction(URI fault) {
        this.faultAction = fault;
    }

    public URI getFaultTo() {
        return faultTo;
    }

    /**
     * Signifies the value for the faultAction response WS-Addressing
     * <code>FaultTo</code> header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setFaultTo(String faultTo) throws URISyntaxException {
        if (StringUtils.hasText(faultTo)) {
            setFaultTo(new URI(faultTo));
        }
    }

    /**
     * Signifies the value for the faultAction response WS-Addressing
     * <code>FaultTo</code> header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setFaultTo(URI faultTo) {
        this.faultTo = faultTo;
    }

    public URI getReplyTo() {
        return replyTo;
    }

    /**
     * Signifies the value for the replyTo response WS-Addressing
     * <code>ReplyTo</code> header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setReplyTo(String replyToAction) throws URISyntaxException {
        if (StringUtils.hasText(replyToAction)) {
            setReplyTo(new URI(replyToAction));
        }
    }

    /**
     * Signifies the value for the replyTo response WS-Addressing
     * <code>ReplyTo</code> header that is provided by the method.
     *
     * @see {@link Action}
     */
    public void setReplyTo(URI replyToAction) {
        this.replyTo = replyToAction;
    }

    public WebServiceMessageSender getMessageSender() {
        return messageSender;
    }

    /**
     * Option to provide a custom WebServiceMessageSender. For example to perform authentication or use alternative transports
     */
    public void setMessageSender(WebServiceMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public MessageIdStrategy getMessageIdStrategy() {
        return messageIdStrategy;
    }

    /**
     * Option to provide a custom MessageIdStrategy to control generation of unique message ids.
     */
    public void setMessageIdStrategy(MessageIdStrategy messageIdStrategy) {
        this.messageIdStrategy = messageIdStrategy;
    }

    public boolean isAllowResponseHeaderOverride() {
        return allowResponseHeaderOverride;
    }

    /**
     * Option to override soap response header in in/out exchange with header info from the actual service layer.
     * If the invoked service appends or rewrites the soap header this option when set to true, allows the modified
     * soap header to be overwritten in in/out message headers
     * 
     * @param allowResponseHeaderOverride - true, will override header with spring-ws response message header
     */
    public void setAllowResponseHeaderOverride(boolean allowResponseHeaderOverride) {
        this.allowResponseHeaderOverride = allowResponseHeaderOverride;
    }

    public boolean isAllowResponseAttachmentOverride() {
        return allowResponseAttachmentOverride;
    }

    /**
     * Option to override soap response attachments in in/out exchange with attachments from the actual service layer.
     * If the invoked service appends or rewrites the soap attachments this option when set to true, allows the modified
     * soap attachments to be overwritten in in/out message attachments
     * 
     * @param allowResponseAttachmentOverride - true, will override attachments with spring-ws response message attachments
     */
    public void setAllowResponseAttachmentOverride(boolean allowResponseAttachmentOverride) {
        this.allowResponseAttachmentOverride = allowResponseAttachmentOverride;
    }
}
