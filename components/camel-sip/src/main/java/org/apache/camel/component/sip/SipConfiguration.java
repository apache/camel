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
package org.apache.camel.component.sip;

import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.SipFactory;
import javax.sip.SipStack;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.ExtensionHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class SipConfiguration {    
    private static final Logger LOG = LoggerFactory.getLogger(SipConfiguration.class);
    private static final String IMPLEMENTATION = "gov.nist";

    private SipComponent component;

    private String protocol;
    private Map<String, Object> parameters;

    @UriPath @Metadata(required = "true")
    private URI uri;
    @UriParam(label = "advanced")
    private AddressFactory addressFactory;
    @UriParam(label = "advanced")
    private MessageFactory messageFactory;
    @UriParam(label = "advanced")
    private HeaderFactory headerFactory;
    @UriParam(label = "advanced")
    private SipStack sipStack;
    @UriParam(label = "advanced")
    private ListeningPoint listeningPoint;
    @UriParam(label = "advanced")
    private SipURI sipUri;
    @UriParam(label = "common", defaultValue = "NAME_NOT_SET")
    private String stackName = "NAME_NOT_SET";
    @UriParam(label = "common", defaultValue = "tcp", enums = "tcp,udp")
    private String transport = "tcp";
    @UriParam(label = "proxy")
    private int maxForwards;
    @UriParam(label = "consumer")
    private boolean consumer;
    @UriParam(label = "common")
    private String eventHeaderName;
    @UriParam(label = "common")
    private String eventId;
    @UriParam(label = "common", defaultValue = "3600")
    private int msgExpiration = 3600;
    @UriParam(label = "proxy")
    private boolean useRouterForAllUris;
    @UriParam(label = "common", defaultValue = "10000")
    private long receiveTimeoutMillis = 10000;
    @UriParam(label = "advanced", defaultValue = "1048576")
    private int maxMessageSize = 1048576;
    @UriParam(label = "common")
    private boolean cacheConnections;
    @UriParam(label = "common", defaultValue = "text")
    private String contentType = "text";
    @UriParam(label = "common", defaultValue = "plain")
    private String contentSubType = "plain";
    @UriParam(label = "logging")
    private String implementationServerLogFile;
    @UriParam(label = "logging")
    private String implementationDebugLogFile;
    @UriParam(label = "logging", defaultValue = "0")
    private String implementationTraceLevel = "0";
    @UriParam(label = "advanced")
    private SipFactory sipFactory;
    @UriParam(label = "common")
    private String fromUser;
    @UriParam(label = "common")
    private String fromHost;
    @UriParam(label = "common")
    private int fromPort;
    @UriParam(label = "common")
    private String toUser;
    @UriParam(label = "common")
    private String toHost;
    @UriParam(label = "common")
    private int toPort;
    @UriParam(label = "consumer")
    private boolean presenceAgent;
    @UriParam(label = "advanced")
    private FromHeader fromHeader;
    @UriParam(label = "advanced")
    private ToHeader toHeader;
    @UriParam(label = "advanced")
    private List<ViaHeader> viaHeaders;
    @UriParam(label = "advanced")
    private ContentTypeHeader contentTypeHeader;
    @UriParam(label = "advanced")
    private CallIdHeader callIdHeader;
    @UriParam(label = "advanced")
    private MaxForwardsHeader maxForwardsHeader;
    @UriParam(label = "advanced")
    private ContactHeader contactHeader;
    @UriParam(label = "advanced")
    private EventHeader eventHeader;
    @UriParam(label = "advanced")
    private ExtensionHeader extensionHeader;
    @UriParam(label = "advanced")
    private ExpiresHeader expiresHeader;

    public SipConfiguration() {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName(IMPLEMENTATION);
    }
    
    public void initialize(URI uri, Map<String, Object> parameters, SipComponent component) {
        this.setParameters(parameters);
        this.setComponent(component);
        this.setUri(uri);
    }

    public void parseURI() throws Exception {
        protocol = uri.getScheme();
        
        if ((!protocol.equalsIgnoreCase("sip")) && (!protocol.equalsIgnoreCase("sips"))) {
            throw new IllegalArgumentException("Unrecognized SIP protocol: " + protocol + " for uri: " + uri);
        }

        Map<String, Object> settings = URISupport.parseParameters(uri);        

        if (settings.containsKey("stackName")) {
            setStackName((String) settings.get("stackName"));
        }
        if (settings.containsKey("transport")) {
            setTransport((String) settings.get("transport"));
        } 
        if (settings.containsKey("maxMessageSize")) {
            setMaxMessageSize(Integer.parseInt((String) settings.get("maxMessageSize")));
        } 
        if (settings.containsKey("cacheConnections")) {
            setCacheConnections(Boolean.valueOf((String) settings.get("cacheConnections")));
        }
        if (settings.containsKey("contentType")) {
            setContentType((String) settings.get("contentType"));
        }
        if (settings.containsKey("contentSubType")) {
            setContentSubType((String) settings.get("contentSubType"));
        }
        if (settings.containsKey("maxForwards")) {
            setMaxForwards(Integer.parseInt((String) settings.get("maxForwards")));
        }
        if (settings.containsKey("receiveTimeoutMillis")) {
            setReceiveTimeoutMillis(Long.parseLong((String) settings.get("receiveTimeoutMillis")));
        }
        if (settings.containsKey("eventHeaderName")) {
            setEventHeaderName((String) settings.get("eventHeaderName"));
        } 
        if (settings.containsKey("eventId")) {
            setEventId((String) settings.get("eventId"));
        }
        if (settings.containsKey("useRouterForAllUris")) {
            setUseRouterForAllUris(Boolean.valueOf((String) settings.get("useRouterForAllUris")));
        }
        if (settings.containsKey("msgExpiration")) {
            setMsgExpiration(Integer.parseInt((String) settings.get("msgExpiration")));
        }
        if (settings.containsKey("presenceAgent")) {
            setPresenceAgent(Boolean.valueOf((String) settings.get("presenceAgent")));
        }

        if (!consumer) {
            if (settings.containsKey("fromUser")) {
                setFromUser((String) settings.get("fromUser"));
            }
            if (settings.containsKey("fromHost")) {
                setFromHost((String) settings.get("fromHost"));
            } 
            if (settings.containsKey("fromPort")) {
                setFromPort(Integer.parseInt((String) settings.get("fromPort")));
            } 
            setToUser(uri.getUserInfo());
            setToHost(uri.getHost());
            setToPort(uri.getPort());
        } else {
            setFromUser(uri.getUserInfo());
            setFromHost(uri.getHost());
            setFromPort(uri.getPort());
            if (!presenceAgent) {
                if (settings.containsKey("toUser")) {
                    setToUser((String) settings.get("toUser"));
                }
                if (settings.containsKey("toHost")) {
                    setToHost((String) settings.get("toHost"));
                } 
                if (settings.containsKey("toPort")) {
                    setToPort(Integer.parseInt((String) settings.get("toPort")));
                } 
            }
        }

        implementationDebugLogFile = component.getAndRemoveParameter(parameters, "implementationDebugLogFile", String.class, null);
        implementationServerLogFile = component.getAndRemoveParameter(parameters, "implementationServerLogFile", String.class, null);
        implementationTraceLevel = component.getAndRemoveParameter(parameters, "implementationTraceLevel", String.class, "0");
        
        LOG.trace("Consumer:" + consumer + " StackName:" + stackName);
        LOG.trace("From User: " + getFromUser() + " From host: " + getFromHost() + " From Port: " + getFromPort());
         
        createFactoriesAndHeaders(parameters, component);
        
        sipUri = component.resolveAndRemoveReferenceParameter(parameters, "sipUri", SipURI.class, null);
        if (sipUri == null) {
            sipUri = addressFactory.createSipURI(getToUser(), getToHost() + ":" + getToPort());
        }

        ObjectHelper.notNull(fromUser, "From User");
        ObjectHelper.notNull(fromHost, "From Host");
        ObjectHelper.notNull(fromPort, "From Port");
        ObjectHelper.notNull(eventHeader, "Event Header");
        ObjectHelper.notNull(eventHeaderName, "Event Header Name");        
        ObjectHelper.notNull(eventId, "Event Id");        
    }    

    @SuppressWarnings("unchecked")
    private void createFactoriesAndHeaders(Map<String, Object> parameters, SipComponent component) throws Exception {
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        setMessageFactory(sipFactory.createMessageFactory());
        
        fromHeader = component.resolveAndRemoveReferenceParameter(parameters, "fromHeader", FromHeader.class, null);
        if (fromHeader == null) { 
            createFromHeader();
        }
        if (!presenceAgent) {
            toHeader = component.resolveAndRemoveReferenceParameter(parameters, "toHeader", ToHeader.class, null);
            if (toHeader == null) {
                createToHeader();
            }
        }
        viaHeaders = component.resolveAndRemoveReferenceParameter(parameters, "viaHeaders", List.class, null);
        if (viaHeaders == null) {        
            createViaHeaders();
        }
        contentTypeHeader = component.resolveAndRemoveReferenceParameter(parameters, "contentTypeHeader", ContentTypeHeader.class, null);
        if (contentTypeHeader == null) {
            createContentTypeHeader();
        }

        callIdHeader = component.resolveAndRemoveReferenceParameter(parameters, "callIdHeader", CallIdHeader.class, null);
        
        maxForwardsHeader = component.resolveAndRemoveReferenceParameter(parameters, "maxForwardsHeader", MaxForwardsHeader.class, null);
        if (maxForwardsHeader == null) {        
            createMaxForwardsHeader();
        }
        
        // Optional Headers
        eventHeader = component.resolveAndRemoveReferenceParameter(parameters, "eventHeader", EventHeader.class, null);
        if (eventHeader == null) {
            createEventHeader();
        }        
        contactHeader = component.resolveAndRemoveReferenceParameter(parameters, "contactHeader", ContactHeader.class, null);
        if (contactHeader == null) {
            createContactHeader();
        }
        expiresHeader = component.resolveAndRemoveReferenceParameter(parameters, "expiresHeader", ExpiresHeader.class, null);
        if (expiresHeader == null) {
            createExpiresHeader();
        }
        extensionHeader = component.resolveAndRemoveReferenceParameter(parameters, "extensionHeader", ExtensionHeader.class, null);
    }

    public Request createSipRequest(long sequenceNumber, String requestMethod, Object body) throws ParseException, InvalidArgumentException {
        //SipConfiguration configuration = sipPublisher.getConfiguration();
        CSeqHeader cSeqHeader = getHeaderFactory().createCSeqHeader(sequenceNumber, requestMethod);

        // Create the request.
        Request request = getMessageFactory().createRequest(
            getSipUri(), 
            requestMethod, 
            getCallIdHeader(), 
            cSeqHeader, 
            getFromHeader(),
            getToHeader(), 
            getViaHeaders(), 
            getMaxForwardsHeader());
        
        if (getEventHeader() != null) {
            request.addHeader(getEventHeader());
        }
        if (getExpiresHeader() != null) {
            request.addHeader(getExpiresHeader());
        }
        if (getContactHeader() != null) {
            request.addHeader(getContactHeader());
        }
        if (getExtensionHeader() != null) {
            request.addHeader(getExtensionHeader());
        }
        request.setContent(body, getContentTypeHeader());
        
        return request;       
    }
    
    private void createFromHeader() throws ParseException {
        SipURI fromAddress = getAddressFactory().createSipURI(getFromUser(), getFromHost());
        fromAddress.setPort(Integer.valueOf(getFromPort()).intValue());
        Address fromNameAddress = addressFactory.createAddress(fromAddress);
        fromNameAddress.setDisplayName(getFromUser());
        
        setFromHeader(headerFactory.createFromHeader(fromNameAddress, getFromUser() + "_Header"));        
    }
    
    private void createToHeader() throws ParseException {
        SipURI toAddress = getAddressFactory().createSipURI(getToUser(), getToHost());
        toAddress.setPort(getToPort());
        Address toNameAddress = addressFactory.createAddress(toAddress);
        toNameAddress.setDisplayName(getToUser());
        
        setToHeader(headerFactory.createToHeader(toNameAddress, getToUser() + "_Header"));
    }

    private void createViaHeaders() throws ParseException, InvalidArgumentException {
        viaHeaders = new ArrayList<ViaHeader>();
        ViaHeader viaHeader = headerFactory.createViaHeader(getFromHost(), getFromPort(),
                getTransport(), null);

        viaHeaders.add(viaHeader);       
    }

    private void createContentTypeHeader() throws ParseException {
        setContentTypeHeader(headerFactory.createContentTypeHeader(getContentType(), getContentSubType()));   
    }
    
    private void createMaxForwardsHeader() throws ParseException, InvalidArgumentException {
        setMaxForwardsHeader(headerFactory.createMaxForwardsHeader(getMaxForwards()));   
    }

    private void createEventHeader() throws ParseException {
        eventHeader = getHeaderFactory().createEventHeader(getEventHeaderName());
        eventHeader.setEventId(getEventId());        
    }
    
    private void createContactHeader() throws ParseException {
        SipURI contactURI = addressFactory.createSipURI(getFromUser(), getFromHost());
        contactURI.setTransportParam(getTransport());
        contactURI.setPort(Integer.valueOf(getFromPort()).intValue());
        Address contactAddress = addressFactory.createAddress(contactURI);

        // Add the contact address.
        contactAddress.setDisplayName(getFromUser());

        contactHeader = headerFactory.createContactHeader(contactAddress);
    }

    private void createExpiresHeader() throws ParseException, InvalidArgumentException {
        expiresHeader = getHeaderFactory().createExpiresHeader(getMsgExpiration());        
    }
    
    Properties createInitialProperties() {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", getStackName());
        properties.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "" + getMaxMessageSize());
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "" + isCacheConnections());
        properties.setProperty("javax.sip.USE_ROUTER_FOR_ALL_URIS", "" + isUseRouterForAllUris());
        if ((implementationDebugLogFile != null) && (implementationServerLogFile != null)) {
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", implementationDebugLogFile);
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", implementationServerLogFile);
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", implementationTraceLevel);
        }
        
        return properties;
    }

    public AddressFactory getAddressFactory() {
        return addressFactory;
    }

    /**
     * To use a custom AddressFactory
     */
    public void setAddressFactory(AddressFactory addressFactory) {
        this.addressFactory = addressFactory;
    }

    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * To use a custom MessageFactory
     */
    public void setMessageFactory(MessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public HeaderFactory getHeaderFactory() {
        return headerFactory;
    }

    /**
     * To use a custom HeaderFactory
     */
    public void setHeaderFactory(HeaderFactory headerFactory) {
        this.headerFactory = headerFactory;
    }

    public SipStack getSipStack() {
        return sipStack;
    }

    /**
     * To use a custom SipStack
     */
    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public SipURI getSipUri() {
        return sipUri;
    }

    /**
     * To use a custom SipURI. If none configured, then the SipUri fallback to use the options toUser toHost:toPort
     */
    public void setSipUri(SipURI sipUri) {
        this.sipUri = sipUri;
    }

    public String getStackName() {
        return stackName;
    }

    /**
     * Name of the SIP Stack instance associated with an SIP Endpoint.
     */
    public void setStackName(String stackName) {
        this.stackName = stackName;
    }

    public String getTransport() {
        return transport;
    }

    /**
     * Setting for choice of transport protocol. Valid choices are "tcp" or "udp".
     */
    public void setTransport(String transport) {
        this.transport = transport;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Setting for maximum allowed Message size in bytes.
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public boolean isCacheConnections() {
        return cacheConnections;
    }

    /**
     * Should connections be cached by the SipStack to reduce cost of connection creation. This is useful if the connection is used for long running conversations.
     */
    public void setCacheConnections(boolean cacheConnections) {
        this.cacheConnections = cacheConnections;
    }

    public ListeningPoint getListeningPoint() {
        return listeningPoint;
    }

    /**
     * To use a custom ListeningPoint implementation
     */
    public void setListeningPoint(ListeningPoint listeningPoint) {
        this.listeningPoint = listeningPoint;
    }

    /**
     * Setting for contentType can be set to any valid MimeType.
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * Setting for contentSubType can be set to any valid MimeSubType.
     */
    public void setContentSubType(String contentSubType) {
        this.contentSubType = contentSubType;
    }

    public String getContentSubType() {
        return contentSubType;
    }

    /**
     * Number of maximum proxy forwards
     */
    public void setMaxForwards(int maxForwards) {
        this.maxForwards = maxForwards;
    }

    public int getMaxForwards() {
        return maxForwards;
    }

    /**
     * Setting for specifying amount of time to wait for a Response and/or Acknowledgement can be received from another SIP stack
     */
    public void setReceiveTimeoutMillis(long receiveTimeoutMillis) {
        this.receiveTimeoutMillis = receiveTimeoutMillis;
    }

    public long getReceiveTimeoutMillis() {
        return receiveTimeoutMillis;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setComponent(SipComponent component) {
        this.component = component;
    }

    public SipComponent getComponent() {
        return component;
    }

    public String getImplementationServerLogFile() {
        return implementationServerLogFile;
    }

    /**
     * Name of server log file to use for logging
     */
    public void setImplementationServerLogFile(String implementationServerLogFile) {
        this.implementationServerLogFile = implementationServerLogFile;
    }

    public String getImplementationDebugLogFile() {
        return implementationDebugLogFile;
    }

    /**
     * Name of client debug log file to use for logging
     */
    public void setImplementationDebugLogFile(String implementationDebugLogFile) {
        this.implementationDebugLogFile = implementationDebugLogFile;
    }

    public String getImplementationTraceLevel() {
        return implementationTraceLevel;
    }

    /**
     * Logging level for tracing
     */
    public void setImplementationTraceLevel(String implementationTraceLevel) {
        this.implementationTraceLevel = implementationTraceLevel;
    }

    public SipFactory getSipFactory() {
        return sipFactory;
    }

    /**
     * To use a custom SipFactory to create the SipStack to be used
     */
    public void setSipFactory(SipFactory sipFactory) {
        this.sipFactory = sipFactory;
    }

    public String getFromUser() {
        return fromUser;
    }

    /**
     * Username of the message originator. Mandatory setting unless a registry based custom FromHeader is specified.
     */
    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public String getFromHost() {
        return fromHost;
    }

    /**
     * Hostname of the message originator. Mandatory setting unless a registry based FromHeader is specified
     */
    public void setFromHost(String fromHost) {
        this.fromHost = fromHost;
    }

    public int getFromPort() {
        return fromPort;
    }

    /**
     * Port of the message originator. Mandatory setting unless a registry based FromHeader is specified
     */
    public void setFromPort(int fromPort) {
        this.fromPort = fromPort;
    }

    public String getToUser() {
        return toUser;
    }

    /**
     * Username of the message receiver. Mandatory setting unless a registry based custom ToHeader is specified.
     */
    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public String getToHost() {
        return toHost;
    }

    /**
     * Hostname of the message receiver. Mandatory setting unless a registry based ToHeader is specified
     */
    public void setToHost(String toHost) {
        this.toHost = toHost;
    }

    public int getToPort() {
        return toPort;
    }

    /**
     * Portname of the message receiver. Mandatory setting unless a registry based ToHeader is specified
     */
    public void setToPort(int toPort) {
        this.toPort = toPort;
    }

    public FromHeader getFromHeader() {
        return fromHeader;
    }

    /**
     * A custom Header object containing message originator settings. Must implement the type javax.sip.header.FromHeader
     */
    public void setFromHeader(FromHeader fromHeader) {
        this.fromHeader = fromHeader;
    }

    public ToHeader getToHeader() {
        return toHeader;
    }

    /**
     * A custom Header object containing message receiver settings. Must implement the type javax.sip.header.ToHeader
     */
    public void setToHeader(ToHeader toHeader) {
        this.toHeader = toHeader;
    }

    public List<ViaHeader> getViaHeaders() {
        return viaHeaders;
    }

    /**
     * List of custom Header objects of the type javax.sip.header.ViaHeader.
     * Each ViaHeader containing a proxy address for request forwarding. (Note this header is automatically updated by each proxy when the request arrives at its listener)
     */
    public void setViaHeaders(List<ViaHeader> viaHeaders) {
        this.viaHeaders = viaHeaders;
    }

    public ContentTypeHeader getContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * A custom Header object containing message content details. Must implement the type javax.sip.header.ContentTypeHeader
     */
    public void setContentTypeHeader(ContentTypeHeader contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public CallIdHeader getCallIdHeader() {
        return callIdHeader;
    }

    /**
     * A custom Header object containing call details. Must implement the type javax.sip.header.CallIdHeader
     */
    public void setCallIdHeader(CallIdHeader callIdHeader) {
        this.callIdHeader = callIdHeader;
    }

    public MaxForwardsHeader getMaxForwardsHeader() {
        return maxForwardsHeader;
    }

    /**
     * A custom Header object containing details on maximum proxy forwards.
     * This header places a limit on the viaHeaders possible. Must implement the type javax.sip.header.MaxForwardsHeader
     */
    public void setMaxForwardsHeader(MaxForwardsHeader maxForwardsHeader) {
        this.maxForwardsHeader = maxForwardsHeader;
    }

    public ContactHeader getContactHeader() {
        return contactHeader;
    }

    /**
     * An optional custom Header object containing verbose contact details (email, phone number etc). Must implement the type javax.sip.header.ContactHeader
     */
    public void setContactHeader(ContactHeader contactHeader) {
        this.contactHeader = contactHeader;
    }

    public ExtensionHeader getExtensionHeader() {
        return extensionHeader;
    }

    /**
     * A custom Header object containing user/application specific details. Must implement the type javax.sip.header.ExtensionHeader
     */
    public void setExtensionHeader(ExtensionHeader extensionHeader) {
        this.extensionHeader = extensionHeader;
    }

    /**
     * URI of the SIP server to connect to (the username and password can be included such as: john:secret@myserver:9999)
     */
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    /**
     * This setting is used to determine whether the kind of header (FromHeader,ToHeader etc) that needs to be created for this endpoint
     */
    public void setConsumer(boolean consumer) {
        this.consumer = consumer;
    }

    public boolean isConsumer() {
        return consumer;
    }

    /**
     * A custom Header object containing event details. Must implement the type javax.sip.header.EventHeader
     */
    public void setEventHeader(EventHeader eventHeader) {
        this.eventHeader = eventHeader;
    }

    public EventHeader getEventHeader() {
        return eventHeader;
    }

    /**
     * Setting for a String based event type.
     */
    public void setEventHeaderName(String eventHeaderName) {
        this.eventHeaderName = eventHeaderName;
    }

    public String getEventHeaderName() {
        return eventHeaderName;
    }

    /**
     * Setting for a String based event Id. Mandatory setting unless a registry based FromHeader is specified
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    /**
     * This setting is used when requests are sent to the Presence Agent via a proxy.
     */
    public void setUseRouterForAllUris(boolean useRouterForAllUris) {
        this.useRouterForAllUris = useRouterForAllUris;
    }

    public boolean isUseRouterForAllUris() {
        return useRouterForAllUris;
    }

    public int getMsgExpiration() {
        return msgExpiration;
    }

    /**
     * The amount of time a message received at an endpoint is considered valid
     */
    public void setMsgExpiration(int msgExpiration) {
        this.msgExpiration = msgExpiration;
    }

    public ExpiresHeader getExpiresHeader() {
        return expiresHeader;
    }

    /**
     * A custom Header object containing message expiration details. Must implement the type javax.sip.header.ExpiresHeader
     */
    public void setExpiresHeader(ExpiresHeader expiresHeader) {
        this.expiresHeader = expiresHeader;
    }

    public boolean isPresenceAgent() {
        return presenceAgent;
    }

    /**
     * This setting is used to distinguish between a Presence Agent & a consumer.
     * This is due to the fact that the SIP Camel component ships with a basic Presence Agent (for testing purposes only). Consumers have to set this flag to true.
     */
    public void setPresenceAgent(boolean presenceAgent) {
        this.presenceAgent = presenceAgent;
    }
    
}
