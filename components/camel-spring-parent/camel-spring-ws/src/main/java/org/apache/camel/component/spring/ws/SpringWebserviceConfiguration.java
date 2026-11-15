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
package org.apache.camel.component.spring.ws;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher;
import org.apache.camel.component.spring.ws.bean.CamelSpringWSEndpointMapping;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.apache.camel.component.spring.ws.type.EndpointMappingKey;
import org.apache.camel.component.spring.ws.type.EndpointMappingType;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.springframework.util.StringUtils;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.addressing.messageid.MessageIdStrategy;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.xml.xpath.XPathExpression;

@UriParams
public class SpringWebserviceConfiguration {

    @UriPath(label = "producer",
             description = "The address that the producer sends requests to. Written in the path of the endpoint URI in place"
                           + " of type and lookupKey, for example spring-ws:http://foo.com/bar. The"
                           + " CamelSpringWebserviceEndpointUri header replaces this address for a request that the producer"
                           + " sends. Not used when the WebServiceTemplate of the webServiceTemplate option has a default URI.")
    private String webServiceEndpointUri;

    /* Common configuration */
    @UriParam
    private MessageFilter messageFilter;
    @UriParam(label = "security",
              description = "To use a custom org.apache.camel.support.jsse.SSLContextParameters for the HTTPS requests that the"
                            + " producer sends. Only applied to a message sender of the WebServiceTemplate whose class is"
                            + " exactly HttpUrlConnectionMessageSender, which is the default message sender. For any other"
                            + " message sender, the producer logs a warning and does not apply it. If not set, the component"
                            + " sets it to the global SSL context parameters when it creates the endpoint and its own"
                            + " useGlobalSslContextParameters option is true. The consumer does not use it.")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "common")
    private MessageIdStrategy messageIdStrategy;

    @UriParam(label = "common")
    private HeaderFilterStrategy headerFilterStrategy = new SpringWebserviceHeaderFilterStrategy();

    /* Producer configuration */
    @UriParam(label = "producer")
    private WebServiceTemplate webServiceTemplate;
    @UriParam(label = "producer",
              description = "To use a custom org.springframework.ws.transport.WebServiceMessageSender, for example to perform"
                            + " authentication or to use another transport. The producer sends its requests with it, in place"
                            + " of the message senders of the WebServiceTemplate. With a WSACamelEndpointMapping, Spring-WS"
                            + " sends the response of the consumer with it when the response goes to the WS-Addressing ReplyTo"
                            + " or FaultTo address of the request instead of the connection of the request.")
    private WebServiceMessageSender messageSender;
    @UriParam(label = "producer")
    private WebServiceMessageFactory messageFactory;
    @UriParam(label = "producer",
              description = "The SOAP action of the request that the producer sends. The CamelSpringWebserviceSoapAction header"
                            + " replaces this option for a request that the producer sends.")
    private String soapAction;
    @UriParam(label = "producer",
              description = "The WS-Addressing 1.0 Action header of the request that the producer sends. If set, the producer"
                            + " also adds a MessageID header, a ReplyTo header when replyTo is set, and a FaultTo header when"
                            + " faultTo is set. The CamelSpringWebserviceAddressingAction header replaces this option for a"
                            + " request that the producer sends. If neither is set, the producer adds no WS-Addressing"
                            + " headers.")
    private URI wsAddressingAction;
    @UriParam(label = "producer",
              description = "The WS-Addressing Action header of the response that the consumer returns. Only used when"
                            + " endpointMapping is a WSACamelEndpointMapping. If not set, the Action header of the request"
                            + " followed by the outputActionSuffix of the WSACamelEndpointMapping is used, and that suffix is"
                            + " Response by default. The producer does not use it.")
    private URI outputAction;
    @UriParam(label = "producer",
              description = "The WS-Addressing Action header of a SOAP fault that the consumer returns. Only used when"
                            + " endpointMapping is a WSACamelEndpointMapping. If not set, the Action header of the request"
                            + " followed by the faultActionSuffix of the WSACamelEndpointMapping is used, and that suffix is"
                            + " Response by default. The producer does not use it.")
    private URI faultAction;
    @UriParam(label = "producer",
              description = "The address of the WS-Addressing FaultTo header of the request that the producer sends. Only used"
                            + " when the wsAddressingAction option or the CamelSpringWebserviceAddressingAction header is set."
                            + " The CamelSpringWebserviceAddressingFaultTo header replaces this option for a request that the"
                            + " producer sends.")
    private URI faultTo;
    @UriParam(label = "producer",
              description = "The address of the WS-Addressing ReplyTo header of the request that the producer sends. Only used"
                            + " when the wsAddressingAction option or the CamelSpringWebserviceAddressingAction header is set."
                            + " The CamelSpringWebserviceAddressingReplyTo header replaces this option for a request that the"
                            + " producer sends.")
    private URI replyTo;
    @UriParam(label = "producer",
              description = "The socket read timeout in milliseconds for the requests that the producer sends. Only applied to"
                            + " a message sender of the WebServiceTemplate whose class is exactly"
                            + " HttpUrlConnectionMessageSender, which is the default message sender, or exactly"
                            + " HttpComponentsMessageSender. For any other message sender, the producer logs a warning and does"
                            + " not apply it, so set the timeout on the message sender itself. If not set, the read timeout of"
                            + " the message sender applies. Use 0 for no timeout.")
    private int timeout = -1;
    @UriParam(label = "producer",
              description = "Whether to copy the SOAP header of the response to the Camel message. If true, the producer sets"
                            + " the CamelSpringWebserviceSoapHeader header to the SOAP header of the response, and sets a Camel"
                            + " header for each attribute and each element of that SOAP header. The name of each of these Camel"
                            + " headers is the local part of the name of the attribute or element, and they replace Camel"
                            + " headers of the same name. The names of the SOAP response header attributes and elements are"
                            + " filtered using the headerFilterStrategy, which by default filters out the internal Camel and"
                            + " camel header namespace (case-insensitive).")
    private boolean allowResponseHeaderOverride;
    @UriParam(label = "producer",
              description = "Whether to copy the attachments of the response to the Camel message. If true, the producer adds"
                            + " each attachment of the response to the Camel message under its content id, where it replaces an"
                            + " attachment that the Camel message already has under that id.")
    private boolean allowResponseAttachmentOverride;

    /* Consumer configuration */
    @UriPath(label = "consumer", name = "type",
             description = "The endpoint mapping type, which names the part of a request that is compared with lookupKey to"
                           + " select the requests for the consumer. rootqname: the qualified name of the root element of the"
                           + " payload. soapaction: the SOAP action. uri: the URI of the request. uripath: the path of the URI"
                           + " of the request. xpathresult: the result of the XPath expression of the expression option for the"
                           + " request. action: the WS-Addressing Action header. to: the WS-Addressing To header. beanname: no"
                           + " part of the request, because lookupKey names a CamelEndpointDispatcher bean and an existing"
                           + " Spring-WS endpoint mapping maps requests to that bean. The endpoint URI takes the type as"
                           + " written here, without the underscore of the enum constant, and ignores its case.")
    private EndpointMappingType endpointMappingType;
    @UriPath(label = "consumer", name = "lookupKey",
             description = "The value that is compared with the part of the request that type names. With rootqname, a QName in"
                           + " the form {namespaceURI}localPart, for example {http://example.com/}GetFoo. With uripath, a value"
                           + " that ends with * matches each path that starts with the text before the *. With action, the"
                           + " WS-Addressing Action can be followed by a colon and the To, and with to, the To can be followed"
                           + " by a colon and the Action. A consumer with such a combined value takes precedence over a"
                           + " consumer with the Action or the To alone. With beanname, the name of a CamelEndpointDispatcher"
                           + " bean in the registry.")
    private String endpointMappingLookupKey;
    @UriParam(label = "consumer")
    private String expression;
    private transient XPathExpression xPathExpression;
    @UriParam(label = "consumer",
              description = "The org.apache.camel.component.spring.ws.bean.CamelSpringWSEndpointMapping bean that maps requests"
                            + " to the consumer. Required for every type except beanname. Use a CamelEndpointMapping for the"
                            + " types rootqname, soapaction, uri, uripath and xpathresult, and a WSACamelEndpointMapping for"
                            + " the types action and to. The consumer registers itself with the bean when it starts, so one"
                            + " bean can serve all consumers of its types. The MessageDispatcher of Spring-WS must use the same"
                            + " bean, which it does by default for each EndpointMapping bean in its ApplicationContext.")
    private CamelSpringWSEndpointMapping endpointMapping;
    @UriParam(label = "consumer",
              description = "The org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher that passes requests to the"
                            + " consumer. An existing Spring-WS endpoint mapping, such as a PayloadRootQNameEndpointMapping or"
                            + " a SoapActionEndpointMapping, maps requests to this bean. When type is beanname, the component"
                            + " sets this option to the CamelEndpointDispatcher bean in the registry whose name is lookupKey.")
    private CamelEndpointDispatcher endpointDispatcher;

    public WebServiceTemplate getWebServiceTemplate() {
        return webServiceTemplate;
    }

    /**
     * Option to provide a custom WebServiceTemplate. This allows for full control over client-side web services
     * handling; like adding a custom interceptor or specifying a fault resolver, message sender or message factory.
     */
    public void setWebServiceTemplate(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public WebServiceMessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * Option to provide a custom WebServiceMessageFactory.
     */
    public void setMessageFactory(WebServiceMessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public String getWebServiceEndpointUri() {
        return webServiceEndpointUri;
    }

    public void setWebServiceEndpointUri(String webServiceEndpointUri) {
        this.webServiceEndpointUri = webServiceEndpointUri;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }

    public String getEndpointUri() {
        if (getEndpointMappingKey() != null) {
            // only for consumers, use lookup key as endpoint uri/key
            return encode(getEndpointMappingKey().getLookupKey());
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
        if (endpointMappingType != null && endpointMappingLookupKey != null) {
            return new EndpointMappingKey(endpointMappingType, endpointMappingLookupKey, xPathExpression);
        } else {
            return null;
        }
    }

    public EndpointMappingType getEndpointMappingType() {
        return endpointMappingType;
    }

    public void setEndpointMappingType(EndpointMappingType endpointMappingType) {
        this.endpointMappingType = endpointMappingType;
    }

    public String getEndpointMappingLookupKey() {
        return endpointMappingLookupKey;
    }

    public void setEndpointMappingLookupKey(String endpointMappingLookupKey) {
        this.endpointMappingLookupKey = endpointMappingLookupKey;
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

    public XPathExpression getxPathExpression() {
        return xPathExpression;
    }

    public void setxPathExpression(XPathExpression xPathExpression) {
        this.xPathExpression = xPathExpression;
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

    public static String encode(String uri) {
        int i = uri.lastIndexOf('}');
        return i == -1 ? uri : (uri.subSequence(0, i) + ")" + uri.substring(i + 1)).replaceFirst("\\{", "(");
    }

    public static String decode(String uri) {
        int i = uri.lastIndexOf(')');
        return i == -1 ? uri : (uri.subSequence(0, i) + "}" + uri.substring(i + 1)).replaceFirst("\\(", "{");
    }

    /**
     * Option to provide a custom MessageFilter. For example when you want to process your headers or attachments by
     * your own.
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

    public void setFaultAction(String fault) throws URISyntaxException {
        if (StringUtils.hasText(fault)) {
            setFaultAction(new URI(fault));
        }
    }

    public void setFaultAction(URI fault) {
        this.faultAction = fault;
    }

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

    public WebServiceMessageSender getMessageSender() {
        return messageSender;
    }

    public void setMessageSender(WebServiceMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public MessageIdStrategy getMessageIdStrategy() {
        return messageIdStrategy;
    }

    /**
     * Option to provide a custom MessageIdStrategy to control generation of WS-Addressing unique message ids.
     */
    public void setMessageIdStrategy(MessageIdStrategy messageIdStrategy) {
        this.messageIdStrategy = messageIdStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter headers mapped to and from the Camel message. By default the
     * internal {@code Camel} and {@code camel} header namespace (case-insensitive) is filtered out from inbound SOAP
     * headers, and the default message filter does not write it into outbound SOAP headers.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isAllowResponseHeaderOverride() {
        return allowResponseHeaderOverride;
    }

    public void setAllowResponseHeaderOverride(boolean allowResponseHeaderOverride) {
        this.allowResponseHeaderOverride = allowResponseHeaderOverride;
    }

    public boolean isAllowResponseAttachmentOverride() {
        return allowResponseAttachmentOverride;
    }

    public void setAllowResponseAttachmentOverride(boolean allowResponseAttachmentOverride) {
        this.allowResponseAttachmentOverride = allowResponseAttachmentOverride;
    }
}
