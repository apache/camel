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

/**
 * Stores information used for sending and receiving SIP messages.
 */
@UriParams
public class SipConfiguration {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SipConfiguration.class);

    /**
     * The SIP library implementation used by the SipFactory for this camel component.
     */
    private static final String IMPLEMENTATION = "gov.nist";

    /**
     * The SipComponent which uses this SipConfiguration to create a SipEndpoint. Primarily used to
     * help with resolving the parameter list given when creating an endpoint.
     */
    private SipComponent component;

    /**
     * The specified SIP Protocol that should be used. Has to be equal to either "sip" or "sips" to be valid.
     */
    private String protocol;

    /**
     * A map of parameters given in the sip URI when the SipEndpoint is created.
     */
    private Map<String, Object> parameters;

    /**
     * The SIP URI the SipEndpoint needs to connect to.
     * This object represents the SIP URI string given when the SipEndpoint is created.
     */
    @UriPath @Metadata(required = "true")
    private URI uri;

    /**
     * The SIP URI the SipEndpoint needs to connect to. This object represents the SIP uri string given
     * when the SipEndpoint is created. This object gets created through the normal URI object.
     */
    @UriParam(label = "advanced")
    private SipURI sipUri;

    /**
     * The name of the SipStack. Defaults to "NAME_NOT_SET".
     */
    @UriParam(label = "common", defaultValue = "NAME_NOT_SET")
    private String stackName = "NAME_NOT_SET";

    /**
     * The transport method used to send and receive messages. Defaults to TCP.
     * Has to be equal to TCP or UDP to be valid.
     */
    @UriParam(label = "common", defaultValue = "tcp", enums = "tcp,udp,tls")
    private String transport = "tcp";

    /**
     * The amount of times a SIP message is allowed to be forwarded. Defaults to 70.
     */
    @UriParam(label = "proxy", defaultValue = "70")
    private int maxForwards = 70;

    /**
     * The amount of time a message received at an endpoint is considered valid.
     */
    @UriParam(label = "common", defaultValue = "3600")
    private int msgExpiration = 3600;

    /**
     * Determines whether requests are send via proxies.
     */
    @UriParam(label = "proxy")
    private boolean useRouterForAllUris;

    /**
     * The amount of time to wait for a Response and/or Acknowledgement message to be received
     * from another SIP stack. Defaults to 10 seconds.
     */
    @UriParam(label = "common", defaultValue = "10000")
    private long receiveTimeoutMillis = 10000;

    /**
     * The maximum size of a message in bytes. Defaults to 1,048,576 B, roughly 1.05 MB.
     */
    @UriParam(label = "advanced", defaultValue = "1048576")
    private int maxMessageSize = 1048576;

    /**
     * Whether connections should be cashed by the SipStack. This if useful for long
     * running conversations as cashing reduces the cost of connection creations.
     */
    @UriParam(label = "common")
    private boolean cacheConnections; //todo check if this is used anywhere

    /**
     * The mime type of the body of the SIP message. Defaults to text.
     */
    @UriParam(label = "common", defaultValue = "text")
    private String contentType = "text";

    /**
     * The mime subtype used in the body of the SIP message. Defaults to plain.
     */
    @UriParam(label = "common", defaultValue = "plain")
    private String contentSubType = "plain";

    /**
     * Name of server log file to use for logging.
     */
    @UriParam(label = "logging")
    private String implementationServerLogFile;

    /**
     * Name of client debug log file to use for logging.
     */
    @UriParam(label = "logging")
    private String implementationDebugLogFile;

    /**
     * Logging level for tracing. Defaults to 0.
     */
    @UriParam(label = "logging", defaultValue = "0")
    private String implementationTraceLevel = "0";

    /**
     * Whether the SipEndpoint is a consuming or producing endpoint. Determines if
     * From headers are used (and thus the Endpoint is consuming because it <b>retrieves from</b> the SIP URI
     * or
     * To headers are used (and thus the Endpoint is producing because it needs to <b>send to</b> the SIP URI.
     */
    @UriParam(label = "consumer")
    private boolean consumer;

    /**
     * Whether the sip consumer will subscribe to the given address or only listen for incoming
     * MESSAGE requests
     */
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean subscribing = false;

    /**
     * The Consumer created by the SipEndpoint will be a SipPresenceAgent when true or
     * SipConsumer when false. A SipPresenceAgent is only for testing purposes. If the endpoint
     * is a consumer this should be false.
     */
    @UriParam(label = "consumer")
    private boolean presenceAgent;

    /**
     * The name of the events in notify/subscribe messages in String format.
     * Mandatory setting unless a registry based FromHeader is specified.
     */
    @UriParam(label = "common")
    private String eventHeaderName;

    /**
     * An optional event ID which can be added the EventHeader
     */
    @UriParam(label = "common")
    private String eventId;

    /**
     * Singleton factory for obtaining for the AddressFactory, HeaderFactory, MessageFactory and SipStack.
     */
    @UriParam(label = "advanced")
    private SipFactory sipFactory;

    /**
     * Allows the creation of (SIP) uri's.
     */
    @UriParam(label = "advanced")
    private AddressFactory addressFactory;

    /**
     * Allows the creation of SIP request and response messages.
     */
    @UriParam(label = "advanced")
    private MessageFactory messageFactory;

    /**
     * Allows the creation of the headers which are placed in the header field at the top of a SIP packet.
     */
    @UriParam(label = "advanced")
    private HeaderFactory headerFactory;

    /**
     * Allows the creation of SipProviders and SipListeners.
     */
    @UriParam(label = "advanced")
    private SipStack sipStack; //does not get used currently?

    /**
     * The socket that a SipProvider uses to send and receive SIP messages.
     */
    @UriParam(label = "advanced")
    private ListeningPoint listeningPoint;

    /**
     * Holds the username of the initiator/sender of a message.
     */
    @UriParam(label = "common")
    private String fromUser;

    /**
     * Holds the host of the initiator/sender of a message.
     */
    @UriParam(label = "common")
    private String fromHost;

    /**
     * Holds the port of the initiator/sender of a message.
     */
    @UriParam(label = "common")
    private int fromPort;

    /**
     * Holds the username of the receiver of a message. Mandatory when subscribing
     */
    @UriParam(label = "common")
    private String toUser;

    /**
     * Holds the host of the receiver of a message. Mandatory when subscribing
     */
    @UriParam(label = "common")
    private String toHost;

    /**
     * Holds the port of the receiver of a message. Mandatory when subscribing
     */
    @UriParam(label = "common")
    private int toPort;

    /**
     * Holds the from header which stores the original sender of a message.
     */
    @UriParam(label = "advanced")
    private FromHeader fromHeader;

    /**
     * Holds the to header which stores the original retriever of a message.
     */
    @UriParam(label = "advanced")
    private ToHeader toHeader;

    /**
     * Holds the Header which stores all proxies which forwarded a message from the sender to the receiver.
     */
    @UriParam(label = "advanced")
    private List<ViaHeader> viaHeaders;

    /**
     * Holds the header which stores the content type of a message.
     */
    @UriParam(label = "advanced")
    private ContentTypeHeader contentTypeHeader;

    /**
     * Holds the header which stores the Call-ID. It is used to uniquely identify a message.
     */
    @UriParam(label = "advanced")
    private CallIdHeader callIdHeader;

    /**
     * Holds the header which stores how many times a message can (still) be forwarded.
     */
    @UriParam(label = "advanced")
    private MaxForwardsHeader maxForwardsHeader;

    /**
     * Holds the header which stores the address of a request originator. The address can then be cashed by the
     * receiver to bypass sip proxies.
     */
    @UriParam(label = "advanced")
    private ContactHeader contactHeader;

    /**
     * Holds the header which stores the event package a message would like to subscribe to or is being
     * notified of. An event header requires to have an event name and can optionally hold an event id.
     */
    @UriParam(label = "advanced")
    private EventHeader eventHeader;

    /**
     * Holds the header which stores user specific data.
     */
    @UriParam(label = "advanced")
    private ExtensionHeader extensionHeader;

    /**
     *  Holds the header which stores the amount of time the request or message-content is valid.
     */
    @UriParam(label = "advanced")
    private ExpiresHeader expiresHeader;


    /**
     * Constructs a configuration instance with every instance variable set to null or their default value
     * expect the SipFactory
     */
    public SipConfiguration() {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName(IMPLEMENTATION);
    }

    /**
     * Initialize the configuration with the SIP uri and parameters
     *
     * @param uri The SIP URI of the server the endpoint is connecting to
     * @param parameters additional parameters used for connecting to the SIP URI
     * @param component the SipComponent creating this SipConfiguration instance
     */
    public void initialize(URI uri, Map<String, Object> parameters, SipComponent component) {
        this.setParameters(parameters);
        this.setComponent(component);
        this.setUri(uri);
    }

    /**
     * Creates all instances needed in this configuration based on the URI and the given parameters. Boolean values
     * consumer and presenceAgent should be set before parsing the URI.
     *
     * @throws Exception when uri protocol does not match "sip" or "sips" or when important variables
     * end up as null.
     */
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
            setTransport(((String) settings.get("transport")).toLowerCase());
        } 
        if (settings.containsKey("maxMessageSize")) {
            setMaxMessageSize(Integer.valueOf((String) settings.get("maxMessageSize")));
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
            setUseRouterForAllUris(Boolean.valueOf((String) settings.get("useRouterForAllUris")));
        }
        if (settings.containsKey("msgExpiration")) {
            setMsgExpiration(Integer.valueOf((String) settings.get("msgExpiration")));
        }
        if (settings.containsKey("presenceAgent")) {
            setPresenceAgent(Boolean.valueOf((String) settings.get("presenceAgent")));
        }
        if (settings.containsKey("subscribing"))
        {
            setSubscribing(Boolean.valueOf((String) settings.get("subscribing")));
        }

        /*
         If the endpoint is a producer endpoint it will send information to the specified server at the SIP URI:
            the ToUser, ToHost and ToPort variables are retrieved from the URI and
            the FromUser, FromHost and FromPort variables must be given as parameters
        */
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
        }
        /*
        if the endpoint is a consumer endpoint it will retrieve information from the specified server at the SIP URI:
            the FromUser, FromHost and FromPort variables retrieved from the URI
            the ToUser, ToHost and ToPort variables are not needed unless the endpoint is a PresenceAgent
            and thus only for testing purposes
        */
        else {
            setFromUser(uri.getUserInfo());
            setFromHost(uri.getHost());
            setFromPort(uri.getPort());
            if (!presenceAgent || subscribing) { //only true when a PresenceAgent and thus not a consumer
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

        implementationDebugLogFile = component.getAndRemoveParameter(parameters, "implementationDebugLogFile", String.class, null);
        implementationServerLogFile = component.getAndRemoveParameter(parameters, "implementationServerLogFile", String.class, null);
        implementationTraceLevel = component.getAndRemoveParameter(parameters, "implementationTraceLevel", String.class, "0");
        
        LOG.trace("Consumer:" + consumer + " StackName:" + stackName);
        LOG.trace("From User: " + getFromUser() + " From host: " + getFromHost() + " From Port: " + getFromPort());
         
        this.createFactoriesAndHeaders(parameters, component);

        sipUri = component.resolveAndRemoveReferenceParameter(parameters, "sipUri", SipURI.class, null);
        if (sipUri == null && (!consumer  || subscribing)) {
            sipUri = addressFactory.createSipURI(getToUser(), getToHost() + ":" + getToPort());
        }

        //throws exceptions when these mandatory instances end up as null
        ObjectHelper.notNull(fromUser, "From User");
        ObjectHelper.notNull(fromHost, "From Host");
        ObjectHelper.notNull(fromPort, "From Port");
        ObjectHelper.notNull(eventHeader, "Event Header");
        ObjectHelper.notNull(eventHeaderName, "Event Header Name");        
        ObjectHelper.notNull(eventId, "Event Id");        
    }

    /**
     * Creates the header, address and message factories along with the all the header instances.
     *
     * @param parameters the parameters given when endpoint got created
     * @param component the SipComponent
     * @throws Exception when something goes wrong looking up from the parameters map
     */
    @SuppressWarnings("unchecked")
    private void createFactoriesAndHeaders(Map<String, Object> parameters, SipComponent component) throws Exception {
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        this.setMessageFactory(sipFactory.createMessageFactory());
        
        fromHeader = component.resolveAndRemoveReferenceParameter(parameters, "fromHeader", FromHeader.class, null);
        if (fromHeader == null) { 
            createFromHeader();
        }
        if(!consumer || subscribing) {
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

        //will end up null if not given as parameter. Use the setter to make a new call ID manually
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

    /**
     * Creates a new SIP request based on the stored headers in the configuration instance.
     *
     * @param sequenceNumber The decimal number used in the CSeq header
     * @param requestMethod The SIP method used in the request
     * @param body The body of the request
     *
     * @return A SIP request containing as headers the SIP URI, request method, CallID, CSeq number, From, To, Via
     * and max forwards
     *
     * @throws ParseException when the headers cannot be used to create the request or the sequence number and request
     * method cannot be used
     * @throws InvalidArgumentException when the request method string is not a valid method string
     */
    public Request createSipRequest(long sequenceNumber, String requestMethod, Object body)
            throws ParseException, InvalidArgumentException
    {
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

        // Optional headers.
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

        // Set the body.
        request.setContent(body, getContentTypeHeader());
        
        return request;       
    }

    /**
     * Creates a FromHeader based on the FromUser, FromHost and FromPort instance variables. The tag in the FromHeader
     * is always "<username>_Header".
     *
     * @throws ParseException when parsing the information for the variables goes wrong
     */
    private void createFromHeader() throws ParseException {
        SipURI fromAddress = getAddressFactory().createSipURI(getFromUser(), getFromHost());
        fromAddress.setPort(Integer.valueOf(getFromPort()).intValue());
        Address fromNameAddress = addressFactory.createAddress(fromAddress);
        fromNameAddress.setDisplayName(getFromUser());

        //// FIXME: 18/06/16 tag MUST be globally unique and cryptographically random with at least 32 bits of randomness
        setFromHeader(headerFactory.createFromHeader(fromNameAddress, getFromUser() + "_Header"));
    }

    /**
     * Creates a ToHeader based on the ToUser, ToHost and ToPort instance variables. The tag in the ToHeader
     * is always "<username>_Header".
     *
     * @throws ParseException
     */
    private void createToHeader() throws ParseException {
        SipURI toAddress = getAddressFactory().createSipURI(getToUser(), getToHost());
        toAddress.setPort(getToPort());
        Address toNameAddress = addressFactory.createAddress(toAddress);
        toNameAddress.setDisplayName(getToUser());

        //// FIXME: 18/06/16 tag MUST be globally unique and cryptographically random with at least 32 bits of randomness
        setToHeader(headerFactory.createToHeader(toNameAddress, getToUser() + "_Header"));
    }

    /**
     * Creates the list of ViaHeaders and populates the list with the SIP URI from the sender
     *
     * @throws ParseException
     * @throws InvalidArgumentException
     */
    private void createViaHeaders() throws ParseException, InvalidArgumentException {
        viaHeaders = new ArrayList<ViaHeader>();
        ViaHeader viaHeader = headerFactory.createViaHeader(getFromHost(), getFromPort(),
                getTransport(), null);

        viaHeaders.add(viaHeader);       
    }

    /**
     * Creates the ContentTypeHeader based on the content type and content subtype instance variables
     *
     * @throws ParseException
     */
    private void createContentTypeHeader() throws ParseException {
        setContentTypeHeader(headerFactory.createContentTypeHeader(getContentType(), getContentSubType()));   
    }

    /**
     * Creates the maxForwardHeader based on the maxForwards instance variable
     *
     * @throws ParseException
     * @throws InvalidArgumentException
     */
    private void createMaxForwardsHeader() throws ParseException, InvalidArgumentException {
        setMaxForwardsHeader(headerFactory.createMaxForwardsHeader(getMaxForwards()));   
    }

    /**
     * Creates the EventHeader based on the eventHeaderName and eventID instance variables
     *
     * @throws ParseException
     */
    private void createEventHeader() throws ParseException {
        eventHeader = getHeaderFactory().createEventHeader(getEventHeaderName());
        eventHeader.setEventId(getEventId());        
    }

    /**
     * Creates the ContactHeader based on a contact address made up from the fromUser, fromHost, fromPort and
     * transport instance variables
     *
     * @throws ParseException
     */
    private void createContactHeader() throws ParseException {
        SipURI contactURI = addressFactory.createSipURI(getFromUser(), getFromHost());
        contactURI.setTransportParam(getTransport());
        contactURI.setPort(Integer.valueOf(getFromPort()).intValue());
        Address contactAddress = addressFactory.createAddress(contactURI);

        // Add the contact address.
        contactAddress.setDisplayName(getFromUser());

        contactHeader = headerFactory.createContactHeader(contactAddress);
    }

    /**
     * Creates the ExpiresHeader based on the msgExpiration instance variable
     *
     * @throws ParseException
     * @throws InvalidArgumentException
     */
    private void createExpiresHeader() throws ParseException, InvalidArgumentException {
        expiresHeader = getHeaderFactory().createExpiresHeader(getMsgExpiration());        
    }

    /**
     * creates a properties object holding information about the stack name, the limit on message size,
     * whether connections should be cashed, if messages can be send via proxies and optionally where the logs
     * are located
     *
     * @return a properties object holding the information stated above
     */
    public Properties createInitialProperties() {
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

    /**
     * To use a custom AddressFactory
     */
    public void setAddressFactory(AddressFactory addressFactory) {
        this.addressFactory = addressFactory;
    }

    public AddressFactory getAddressFactory() {
        return addressFactory;
    }

    /**
     * To use a custom MessageFactory
     */
    public void setMessageFactory(MessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * To use a custom HeaderFactory
     */
    public void setHeaderFactory(HeaderFactory headerFactory) {
        this.headerFactory = headerFactory;
    }

    public HeaderFactory getHeaderFactory() {
        return headerFactory;
    }

    /**
     * To use a custom SipStack
     */
    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public SipStack getSipStack() {
        return sipStack;
    }
    /**
     * To use a custom SipURI. If none configured, then the SipUri fallback to use the options toUser toHost:toPort
     */
    public void setSipUri(SipURI sipUri) {
        this.sipUri = sipUri;
    }

    public SipURI getSipUri() {
        return sipUri;
    }

    /**
     * Name of the SIP Stack instance associated with an SIP Endpoint.
     */
    public void setStackName(String stackName) {
        this.stackName = stackName;
    }

    public String getStackName() {
        return stackName;
    }

    /**
     * Setting for choice of transport protocol. Valid choices are "tcp" or "udp".
     */
    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getTransport() {
        return transport;
    }

    /**
     * Setting for maximum allowed Message size in bytes.
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Should connections be cached by the SipStack to reduce cost of connection creation.
     * This is useful if the connection is used for long running conversations.
     */
    public void setCacheConnections(boolean cacheConnections) {
        this.cacheConnections = cacheConnections;
    }

    public boolean isCacheConnections() {
        return cacheConnections;
    }

    /**
     * To use a custom ListeningPoint implementation
     */
    public void setListeningPoint(ListeningPoint listeningPoint) {
        this.listeningPoint = listeningPoint;
    }

    public ListeningPoint getListeningPoint() {
        return listeningPoint;
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
     * Setting for specifying amount of time to wait for a Response and/or Acknowledgement to be received
     * from another SIP stack
     */
    public void setReceiveTimeoutMillis(long receiveTimeoutMillis) {
        this.receiveTimeoutMillis = receiveTimeoutMillis;
    }

    public long getReceiveTimeoutMillis() {
        return receiveTimeoutMillis;
    }

    /**
     * Used for setting the parameters given when the SipComponent made the SipEndpoint
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     *  Used for setting the component which made the SipConfiguration instance
     */
    public void setComponent(SipComponent component) {
        this.component = component;
    }

    public SipComponent getComponent() {
        return component;
    }

    /**
     * Name of server log file to use for logging
     */
    public void setImplementationServerLogFile(String implementationServerLogFile) {
        this.implementationServerLogFile = implementationServerLogFile;
    }

    public String getImplementationServerLogFile() {
        return implementationServerLogFile;
    }

    /**
     * Name of client debug log file to use for logging
     */
    public void setImplementationDebugLogFile(String implementationDebugLogFile) {
        this.implementationDebugLogFile = implementationDebugLogFile;
    }

    public String getImplementationDebugLogFile() {
        return implementationDebugLogFile;
    }

    /**
     * Logging level for tracing
     */
    public void setImplementationTraceLevel(String implementationTraceLevel) {
        this.implementationTraceLevel = implementationTraceLevel;
    }

    public String getImplementationTraceLevel() {
        return implementationTraceLevel;
    }

    /**
     * To use a custom SipFactory to create the SipStack to be used
     */
    public void setSipFactory(SipFactory sipFactory) {
        this.sipFactory = sipFactory;
    }

    public SipFactory getSipFactory() {
        return sipFactory;
    }

    /**
     * Username of the message originator. Mandatory setting unless a registry based custom FromHeader is specified.
     */
    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public String getFromUser() {
        return fromUser;
    }

    /**
     * Hostname of the message originator. Mandatory setting unless a registry based FromHeader is specified
     */
    public void setFromHost(String fromHost) {
        this.fromHost = fromHost;
    }

    public String getFromHost() {
        return fromHost;
    }

    /**
     * Port of the message originator. Mandatory setting unless a registry based FromHeader is specified
     */
    public void setFromPort(int fromPort) {
        this.fromPort = fromPort;
    }

    public int getFromPort(){ return fromPort; }

    /**
     * Username of the message receiver. Mandatory setting unless a registry based custom ToHeader is specified.
     */
    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public String getToUser() {
        return toUser;
    }

    /**
     * Hostname of the message receiver. Mandatory setting unless a registry based ToHeader is specified
     */
    public void setToHost(String toHost) {
        this.toHost = toHost;
    }

    public String getToHost() {
        return toHost;
    }

    /**
     * Portname of the message receiver. Mandatory setting unless a registry based ToHeader is specified
     */
    public void setToPort(int toPort) {
        this.toPort = toPort;
    }

    public int getToPort() {
        return toPort;
    }

    /**
     * A custom Header object containing message originator settings. Must implement the type javax.sip.header.FromHeader
     */
    public void setFromHeader(FromHeader fromHeader) {
        this.fromHeader = fromHeader;
    }

    public FromHeader getFromHeader() {
        return fromHeader;
    }

    /**
     * A custom Header object containing message receiver settings. Must implement the type javax.sip.header.ToHeader
     */
    public void setToHeader(ToHeader toHeader) {
        this.toHeader = toHeader;
    }

    public ToHeader getToHeader() {
        return toHeader;
    }

    /**
     * List of custom Header objects of the type javax.sip.header.ViaHeader.
     * Each ViaHeader containing a proxy address for request forwarding.
     * (Note this header is automatically updated by each proxy when the request arrives at its listener)
     */
    public void setViaHeaders(List<ViaHeader> viaHeaders) {
        this.viaHeaders = viaHeaders;
    }

    public List<ViaHeader> getViaHeaders() {
        return viaHeaders;
    }

    /**
     * A custom Header object containing message content details.
     * Must implement the type javax.sip.header.ContentTypeHeader
     */
    public void setContentTypeHeader(ContentTypeHeader contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public ContentTypeHeader getContentTypeHeader() {
        return contentTypeHeader;
    }

    /**
     * A custom Header object containing call details. Must implement the type javax.sip.header.CallIdHeader
     */
    public void setCallIdHeader(CallIdHeader callIdHeader) {
        this.callIdHeader = callIdHeader;
    }

    public CallIdHeader getCallIdHeader() {
        return callIdHeader;
    }

    /**
     * A custom Header object containing details on maximum proxy forwards.
     * This header places a limit on the viaHeaders possible. Must implement the type javax.sip.header.MaxForwardsHeader
     */
    public void setMaxForwardsHeader(MaxForwardsHeader maxForwardsHeader) {
        this.maxForwardsHeader = maxForwardsHeader;
    }

    public MaxForwardsHeader getMaxForwardsHeader() {
        return maxForwardsHeader;
    }

    /**
     * An optional custom Header object containing verbose contact details (email, phone number etc). Must implement the type javax.sip.header.ContactHeader
     */
    public void setContactHeader(ContactHeader contactHeader) {
        this.contactHeader = contactHeader;
    }

    public ContactHeader getContactHeader() {
        return contactHeader;
    }

    /**
     * A custom Header object containing user/application specific details. Must implement the type javax.sip.header.ExtensionHeader
     */
    public void setExtensionHeader(ExtensionHeader extensionHeader) {
        this.extensionHeader = extensionHeader;
    }

    public ExtensionHeader getExtensionHeader() {
        return extensionHeader;
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
     * This setting is used to determine whether the kind of header
     * (FromHeader,ToHeader etc) that needs to be created for this endpoint
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
     * Setting for a String based event Id. Mandatory setting unless a registry based FromHeader is specified
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

    /**
     * The amount of time a message received at an endpoint is considered valid
     */
    public void setMsgExpiration(int msgExpiration) {
        this.msgExpiration = msgExpiration;
    }

    public int getMsgExpiration() {
        return msgExpiration;
    }

    /**
     * A custom Header object containing message expiration details. Must implement the type javax.sip.header.ExpiresHeader
     */
    public void setExpiresHeader(ExpiresHeader expiresHeader) {
        this.expiresHeader = expiresHeader;
    }

    public ExpiresHeader getExpiresHeader() {
        return expiresHeader;
    }

    /**
     * This setting is used to distinguish between a Presence Agent & a consumer.
     * This is due to the fact that the SIP Camel component ships with a basic Presence Agent (for testing purposes only).
     * Consumers have to set this flag to true.
     */
    public void setPresenceAgent(boolean presenceAgent) {
        this.presenceAgent = presenceAgent;
    }

    public boolean isPresenceAgent() {
        return presenceAgent;
    }

    /**
     * setting for whether the consumer should subscribe
     */
    public void setSubscribing(boolean subscribing)
    {
        this.subscribing = subscribing;
    }

    public boolean isSubscribing()
    {
        return subscribing;
    }

}