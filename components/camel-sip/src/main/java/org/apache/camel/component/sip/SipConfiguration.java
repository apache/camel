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
import java.util.Map;
import java.util.Properties;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
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

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@SuppressWarnings("unchecked")
public class SipConfiguration {    
    private static final transient Log LOG = LogFactory.getLog(SipConfiguration.class);
    private static final String IMPLEMENTATION = "gov.nist";
    private URI uri;
    private Map<String, Object> parameters;
    private SipComponent component;
    private AddressFactory addressFactory;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private SipStack sipStack;
    private ListeningPoint listeningPoint;
    private String protocol;
    private SipURI sipUri;
    private String stackName;
    private String transport;
    private int maxForwards;
    private boolean consumer;
    private String eventHeaderName;
    private String eventId;
    private int msgExpiration;
    private String useRouterForAllUris;
    private long receiveTimeoutMillis;
    private String maxMessageSize;
    private String cacheConnections;
    private String contentType;
    private String contentSubType;
    private String automaticDialogSupport;
    private String nistServerLog;
    private String nistDebugLog;
    private String nistTraceLevel;
    private SipFactory sipFactory;
    private String fromUser;
    private String fromHost;
    private int fromPort;
    private String toUser;
    private String toHost;
    private int toPort;
    private boolean presenceAgent;
    
    private FromHeader fromHeader;
    private ToHeader toHeader;
    private ArrayList<ViaHeader> viaHeaders;
    private ContentTypeHeader contentTypeHeader;
    private CallIdHeader callIdHeader;
    private MaxForwardsHeader maxForwardsHeader;
    private ContactHeader contactHeader;
    private EventHeader eventHeader;
    private ExtensionHeader extensionHeader;
    private ExpiresHeader expiresHeader;
    private ClientTransaction clientTransactionId;
    private Dialog dialog;
    
    public SipConfiguration() {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName(IMPLEMENTATION);
        
        setStackName("NAME_NOT_SET");
        setTransport("tcp");
        setMaxMessageSize("1048576");
        setCacheConnections("false");
        setAutomaticDialogSupport("off");
        setContentType("text");
        setContentSubType("plain");   
        setReceiveTimeoutMillis(10000);
        setConsumer(false);
        setUseRouterForAllUris("false");
        setMsgExpiration(3600);
        setPresenceAgent(false);
    }
    
    public void initialize(URI uri, Map<String, Object> parameters,
            SipComponent component) {
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
            setMaxMessageSize((String) settings.get("maxMessageSize"));
        } 
        if (settings.containsKey("cacheConnections")) {
            setCacheConnections((String) settings.get("cacheConnections"));
        }
        if (settings.containsKey("contentType")) {
            setContentType((String) settings.get("contentType"));
        }
        if (settings.containsKey("contentSubType")) {
            setContentSubType((String) settings.get("contentSubType"));
        }
        if (settings.containsKey("maxForwards")) {
            setMaxForwards(Integer.valueOf((String) settings.get("maxForwards")));
        }
        if (settings.containsKey("receiveTimeoutMillis")) {
            setReceiveTimeoutMillis(Long.valueOf((String) settings.get("receiveTimeoutMillis")));
        }
        if (settings.containsKey("eventHeaderName")) {
            setEventHeaderName((String) settings.get("eventHeaderName"));
        } 
        if (settings.containsKey("eventId")) {
            setEventId((String) settings.get("eventId"));
        }
        if (settings.containsKey("useRouterForAllUris")) {
            setUseRouterForAllUris((String) settings.get("useRouterForAllUris"));
        }
        if (settings.containsKey("msgExpiration")) {
            setMsgExpiration(Integer.valueOf((String) settings.get("msgExpiration")));
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
                setFromPort(Integer.valueOf((String) settings.get("fromPort")));
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
                    setToPort(Integer.valueOf((String) settings.get("toPort")));
                } 
            }
        }
        nistDebugLog = component.getAndRemoveParameter(parameters, "implementationDebugLogFile", String.class, null);
        nistServerLog = component.getAndRemoveParameter(parameters, "implementationServerLogFile", String.class, null);
        nistTraceLevel = component.getAndRemoveParameter(parameters, "implementationTraceLevel", String.class, "0");
        
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
        viaHeaders = component.resolveAndRemoveReferenceParameter(parameters, "viaHeaders", ArrayList.class, null);
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
        viaHeaders = new ArrayList();
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
        properties.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", getMaxMessageSize());
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", getCacheConnections());
        properties.setProperty("javax.sip.USE_ROUTER_FOR_ALL_URIS", getUseRouterForAllUris());
        if ((nistDebugLog != null) && (nistServerLog != null)) {
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", nistDebugLog);
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", nistServerLog);
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", nistTraceLevel);
        }
        
        return properties;
    }

    public AddressFactory getAddressFactory() {
        return addressFactory;
    }

    public void setAddressFactory(AddressFactory addressFactory) {
        this.addressFactory = addressFactory;
    }

    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    public void setMessageFactory(MessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public HeaderFactory getHeaderFactory() {
        return headerFactory;
    }

    public void setHeaderFactory(HeaderFactory headerFactory) {
        this.headerFactory = headerFactory;
    }

    public SipStack getSipStack() {
        return sipStack;
    }

    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public SipURI getSipUri() {
        return sipUri;
    }

    public void setSipUri(SipURI sipUri) {
        this.sipUri = sipUri;
    }

    public String getStackName() {
        return stackName;
    }

    public void setStackName(String stackName) {
        this.stackName = stackName;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(String maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public String getAutomaticDialogSupport() {
        return automaticDialogSupport;
    }

    public void setAutomaticDialogSupport(String automaticDialogSupport) {
        this.automaticDialogSupport = automaticDialogSupport;
    }

    public String getCacheConnections() {
        return cacheConnections;
    }

    public void setCacheConnections(String cacheConnections) {
        this.cacheConnections = cacheConnections;
    }

    public ListeningPoint getListeningPoint() {
        return listeningPoint;
    }

    public void setListeningPoint(ListeningPoint listeningPoint) {
        this.listeningPoint = listeningPoint;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentSubType(String contentSubType) {
        this.contentSubType = contentSubType;
    }

    public String getContentSubType() {
        return contentSubType;
    }

    public void setMaxForwards(int maxForwards) {
        this.maxForwards = maxForwards;
    }

    public int getMaxForwards() {
        return maxForwards;
    }

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

    public String getNistServerLog() {
        return nistServerLog;
    }

    public void setNistServerLog(String nistServerLog) {
        this.nistServerLog = nistServerLog;
    }

    public String getNistDebugLog() {
        return nistDebugLog;
    }

    public void setNistDebugLog(String nistDebugLog) {
        this.nistDebugLog = nistDebugLog;
    }

    public String getNistTraceLevel() {
        return nistTraceLevel;
    }

    public void setNistTraceLevel(String nistTraceLevel) {
        this.nistTraceLevel = nistTraceLevel;
    }

    public SipFactory getSipFactory() {
        return sipFactory;
    }

    public void setSipFactory(SipFactory sipFactory) {
        this.sipFactory = sipFactory;
    }

    public String getFromUser() {
        return fromUser;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public String getFromHost() {
        return fromHost;
    }

    public void setFromHost(String fromHost) {
        this.fromHost = fromHost;
    }

    public int getFromPort() {
        return fromPort;
    }

    public void setFromPort(int fromPort) {
        this.fromPort = fromPort;
    }

    public String getToUser() {
        return toUser;
    }

    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public String getToHost() {
        return toHost;
    }

    public void setToHost(String toHost) {
        this.toHost = toHost;
    }

    public int getToPort() {
        return toPort;
    }

    public void setToPort(int toPort) {
        this.toPort = toPort;
    }

    public FromHeader getFromHeader() {
        return fromHeader;
    }

    public void setFromHeader(FromHeader fromHeader) {
        this.fromHeader = fromHeader;
    }

    public ToHeader getToHeader() {
        return toHeader;
    }

    public void setToHeader(ToHeader toHeader) {
        this.toHeader = toHeader;
    }

    public ArrayList<ViaHeader> getViaHeaders() {
        return viaHeaders;
    }

    public void setViaHeaders(ArrayList<ViaHeader> viaHeaders) {
        this.viaHeaders = viaHeaders;
    }

    public ContentTypeHeader getContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(ContentTypeHeader contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public CallIdHeader getCallIdHeader() {
        return callIdHeader;
    }

    public void setCallIdHeader(CallIdHeader callIdHeader) {
        this.callIdHeader = callIdHeader;
    }

    public MaxForwardsHeader getMaxForwardsHeader() {
        return maxForwardsHeader;
    }

    public void setMaxForwardsHeader(MaxForwardsHeader maxForwardsHeader) {
        this.maxForwardsHeader = maxForwardsHeader;
    }

    public ContactHeader getContactHeader() {
        return contactHeader;
    }

    public void setContactHeader(ContactHeader contactHeader) {
        this.contactHeader = contactHeader;
    }

    public ExtensionHeader getExtensionHeader() {
        return extensionHeader;
    }

    public void setExtensionHeader(ExtensionHeader extensionHeader) {
        this.extensionHeader = extensionHeader;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    public void setConsumer(boolean consumer) {
        this.consumer = consumer;
    }

    public boolean isConsumer() {
        return consumer;
    }

    public void setClientTransactionId(ClientTransaction clientTransactionId) {
        this.clientTransactionId = clientTransactionId;
    }

    public ClientTransaction getClientTransactionId() {
        return clientTransactionId;
    }

    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void setEventHeader(EventHeader eventHeader) {
        this.eventHeader = eventHeader;
    }

    public EventHeader getEventHeader() {
        return eventHeader;
    }

    public void setEventHeaderName(String eventHeaderName) {
        this.eventHeaderName = eventHeaderName;
    }

    public String getEventHeaderName() {
        return eventHeaderName;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setUseRouterForAllUris(String useRouterForAllUris) {
        this.useRouterForAllUris = useRouterForAllUris;
    }

    public String getUseRouterForAllUris() {
        return useRouterForAllUris;
    }

    public int getMsgExpiration() {
        return msgExpiration;
    }

    public void setMsgExpiration(int msgExpiration) {
        this.msgExpiration = msgExpiration;
    }

    public ExpiresHeader getExpiresHeader() {
        return expiresHeader;
    }

    public void setExpiresHeader(ExpiresHeader expiresHeader) {
        this.expiresHeader = expiresHeader;
    }

    public boolean isPresenceAgent() {
        return presenceAgent;
    }

    public void setPresenceAgent(boolean presenceAgent) {
        this.presenceAgent = presenceAgent;
    }
    
}
