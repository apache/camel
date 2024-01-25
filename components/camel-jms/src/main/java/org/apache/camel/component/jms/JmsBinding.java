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
package org.apache.camel.component.jms;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageFormatException;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;

import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsMessageHelper.getSafeLongProperty;
import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;
import static org.apache.camel.component.jms.JmsMessageType.Bytes;
import static org.apache.camel.component.jms.JmsMessageType.Map;
import static org.apache.camel.component.jms.JmsMessageType.Object;
import static org.apache.camel.component.jms.JmsMessageType.Stream;
import static org.apache.camel.component.jms.JmsMessageType.Text;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link JmsMessage} to and from a JMS {@link Message}
 */
public class JmsBinding {
    private static final Logger LOG = LoggerFactory.getLogger(JmsBinding.class);
    private final JmsEndpoint endpoint;
    private final HeaderFilterStrategy headerFilterStrategy;
    private final JmsKeyFormatStrategy jmsKeyFormatStrategy;
    private final MessageCreatedStrategy messageCreatedStrategy;

    public JmsBinding() {
        this.endpoint = null;
        this.headerFilterStrategy = new JmsHeaderFilterStrategy(false);
        this.jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
        this.messageCreatedStrategy = null;
    }

    public JmsBinding(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
        if (endpoint.getHeaderFilterStrategy() != null) {
            this.headerFilterStrategy = endpoint.getHeaderFilterStrategy();
        } else {
            this.headerFilterStrategy = new JmsHeaderFilterStrategy(endpoint.isIncludeAllJMSXProperties());
        }
        if (endpoint.getJmsKeyFormatStrategy() != null) {
            this.jmsKeyFormatStrategy = endpoint.getJmsKeyFormatStrategy();
        } else {
            this.jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
        }
        if (endpoint.getMessageCreatedStrategy() != null) {
            this.messageCreatedStrategy = endpoint.getMessageCreatedStrategy();
        } else if (endpoint.getComponent() != null) {
            // fallback and use from component
            this.messageCreatedStrategy = endpoint.getComponent().getConfiguration().getMessageCreatedStrategy();
        } else {
            this.messageCreatedStrategy = null;
        }
    }

    /**
     * Extracts the body from the JMS message
     *
     * @param  exchange the exchange
     * @param  message  the message to extract its body
     * @return          the body, can be <tt>null</tt>
     */
    public Object extractBodyFromJms(Exchange exchange, Message message) {
        try {
            // is a custom message converter configured on endpoint then use it instead of doing the extraction
            // based on message type
            if (endpoint != null && endpoint.getMessageConverter() != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Extracting body using a custom MessageConverter: {} from JMS message: {}",
                            endpoint.getMessageConverter(), message);
                }
                return endpoint.getMessageConverter().fromMessage(message);
            }

            // if we are configured to not map the jms message then return it as body
            if (endpoint != null && !endpoint.getConfiguration().isMapJmsMessage()) {
                LOG.trace("Option map JMS message is false so using JMS message as body: {}", message);
                return message;
            }

            if (message instanceof ObjectMessage) {
                LOG.trace("Extracting body as a ObjectMessage from JMS message: {}", message);
                ObjectMessage objectMessage = (ObjectMessage) message;
                Object payload = objectMessage.getObject();
                if (payload instanceof DefaultExchangeHolder) {
                    DefaultExchangeHolder holder = (DefaultExchangeHolder) payload;
                    DefaultExchangeHolder.unmarshal(exchange, holder);
                    // enrich with JMS headers also as otherwise they will get lost when use the transferExchange option.
                    Map<String, Object> jmsHeaders = extractHeadersFromJms(message, exchange);
                    exchange.getIn().getHeaders().putAll(jmsHeaders);
                    return exchange.getIn().getBody();
                } else {
                    return objectMessage.getObject();
                }
            } else if (message instanceof TextMessage) {
                LOG.trace("Extracting body as a TextMessage from JMS message: {}", message);
                TextMessage textMessage = (TextMessage) message;
                return textMessage.getText();
            } else if (message instanceof MapMessage) {
                LOG.trace("Extracting body as a MapMessage from JMS message: {}", message);
                return createMapFromMapMessage((MapMessage) message);
            } else if (message instanceof BytesMessage) {
                LOG.trace("Extracting body as a BytesMessage from JMS message: {}", message);
                return createByteArrayFromBytesMessage(exchange, (BytesMessage) message);
            } else if (message instanceof StreamMessage) {
                LOG.trace("Extracting body as a StreamMessage from JMS message: {}", message);
                StreamMessage streamMessage = (StreamMessage) message;
                return createInputStreamFromStreamMessage(streamMessage);
            } else {
                return null;
            }
        } catch (JMSException e) {
            throw new RuntimeCamelException("Failed to extract body due to: " + e + ". Message: " + message, e);
        }
    }

    public Map<String, Object> extractHeadersFromJms(Message jmsMessage, Exchange exchange) {
        Map<String, Object> map = new HashMap<>();
        if (jmsMessage != null) {
            // lets populate the standard JMS message headers
            try {
                map.put(JmsConstants.JMS_HEADER_CORRELATION_ID, JmsMessageHelper.getJMSCorrelationID(jmsMessage));
                if (endpoint == null || endpoint.getComponent().isIncludeCorrelationIDAsBytes()) {
                    map.put(JmsConstants.JMS_HEADER_CORRELATION_ID_AS_BYTES,
                            JmsMessageHelper.getJMSCorrelationIDAsBytes(jmsMessage));
                }
                map.put(JmsConstants.JMS_HEADER_DELIVERY_MODE, jmsMessage.getJMSDeliveryMode());
                map.put(JmsConstants.JMS_HEADER_DESTINATION, jmsMessage.getJMSDestination());
                map.put(JmsConstants.JMS_HEADER_EXPIRATION, jmsMessage.getJMSExpiration());
                map.put(JmsConstants.JMS_HEADER_MESSAGE_ID, jmsMessage.getJMSMessageID());
                map.put(JmsConstants.JMS_HEADER_PRIORITY, jmsMessage.getJMSPriority());
                map.put(JmsConstants.JMS_HEADER_REDELIVERED, jmsMessage.getJMSRedelivered());
                map.put(JmsConstants.JMS_HEADER_TIMESTAMP, jmsMessage.getJMSTimestamp());

                map.put(JmsConstants.JMS_HEADER_REPLY_TO, JmsMessageHelper.getJMSReplyTo(jmsMessage));
                map.put(JmsConstants.JMS_HEADER_TYPE, JmsMessageHelper.getJMSType(jmsMessage));

                // this works around a bug in the ActiveMQ property handling
                map.put(JmsConstants.JMS_X_GROUP_ID,
                        JmsMessageHelper.getStringProperty(jmsMessage, JmsConstants.JMS_X_GROUP_ID));
                map.put(JmsConstants.JMS_HEADER_XUSER_ID,
                        JmsMessageHelper.getStringProperty(jmsMessage, JmsConstants.JMS_HEADER_XUSER_ID));
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }

            Enumeration<?> names;
            try {
                names = jmsMessage.getPropertyNames();
            } catch (JMSException e) {
                throw new RuntimeCamelException(e);
            }
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                try {
                    Object value = JmsMessageHelper.getProperty(jmsMessage, name);
                    if (headerFilterStrategy != null
                            && headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                        continue;
                    }

                    // must decode back from safe JMS header name to original header name
                    // when storing on this Camel JmsMessage object.
                    String key = jmsKeyFormatStrategy.decodeKey(name);
                    map.put(key, value);
                } catch (JMSException e) {
                    throw new RuntimeCamelException(name, e);
                }
            }
        }

        return map;
    }

    /**
     * @deprecated not in use
     */
    @Deprecated
    public Object getObjectProperty(Message jmsMessage, String name) throws JMSException {
        // try a direct lookup first
        Object answer = jmsMessage.getObjectProperty(name);
        if (answer == null) {
            // then encode the key and do another lookup
            String key = jmsKeyFormatStrategy.encodeKey(name);
            answer = jmsMessage.getObjectProperty(key);
        }
        return answer;
    }

    protected Object createByteArrayFromBytesMessage(Exchange exchange, BytesMessage message) throws JMSException {
        // ActiveMQ has special optimised mode for bytes message, so we should use streaming if possible
        boolean artemis = endpoint != null && endpoint.isArtemisStreamingEnabled();
        Long size = artemis ? getSafeLongProperty(message, "_AMQ_LARGE_SIZE") : null;
        if (size != null && size > 0) {
            LOG.trace(
                    "Optimised for Artemis: Reading from BytesMessage in streaming mode directly into CachedOutputStream payload");
            CachedOutputStream cos = new CachedOutputStream(exchange, true);
            // this will save the stream and wait until the entire message is written before continuing.
            message.setObjectProperty("JMS_AMQ_SaveStream", cos);
            try {
                // and then lets get the input stream of this so we can read it
                return cos.getInputStream();
            } catch (IOException e) {
                JMSException cause = new MessageFormatException(e.getMessage());
                cause.initCause(e);
                throw cause;
            } finally {
                IOHelper.close(cos);
            }
        }

        if (message.getBodyLength() > Integer.MAX_VALUE) {
            LOG.warn("Length of BytesMessage is too long: {}", message.getBodyLength());
            return null;
        }
        byte[] result = new byte[(int) message.getBodyLength()];
        message.readBytes(result);
        return result;
    }

    protected Object createInputStreamFromStreamMessage(StreamMessage message) {
        return new StreamMessageInputStream(message);
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param  exchange     the current exchange
     * @param  session      the JMS session used to create the message
     * @return              a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, Session session) throws JMSException {
        Message answer = makeJmsMessage(exchange, exchange.getIn(), session, null);
        if (answer != null && messageCreatedStrategy != null) {
            messageCreatedStrategy.onMessageCreated(answer, session, exchange, null);
        }
        return answer;
    }

    /**
     * Creates a JMS message from the Camel exchange and message
     *
     * @param  exchange     the current exchange
     * @param  camelMessage the body to make a jakarta.jms.Message as
     * @param  session      the JMS session used to create the message
     * @param  cause        optional exception occurred that should be sent as reply instead of a regular body
     * @return              a newly created JMS Message instance containing the
     * @throws JMSException if the message could not be created
     */
    public Message makeJmsMessage(Exchange exchange, org.apache.camel.Message camelMessage, Session session, Exception cause)
            throws JMSException {
        Message answer = null;

        boolean alwaysCopy = endpoint != null && endpoint.getConfiguration().isAlwaysCopyMessage();
        boolean force = endpoint != null && endpoint.getConfiguration().isForceSendOriginalMessage();
        if (!alwaysCopy && camelMessage instanceof JmsMessage) {
            JmsMessage jmsMessage = (JmsMessage) camelMessage;
            if (!jmsMessage.shouldCreateNewMessage() || force) {
                answer = jmsMessage.getJmsMessage();

                if (!force) {
                    // answer must match endpoint type
                    JmsMessageType type = endpoint != null ? endpoint.getConfiguration().getJmsMessageType() : null;
                    if (type != null && answer != null) {
                        if (type == JmsMessageType.Text) {
                            answer = answer instanceof TextMessage ? answer : null;
                        } else if (type == JmsMessageType.Bytes) {
                            answer = answer instanceof BytesMessage ? answer : null;
                        } else if (type == JmsMessageType.Map) {
                            answer = answer instanceof MapMessage ? answer : null;
                        } else if (type == JmsMessageType.Object) {
                            answer = answer instanceof ObjectMessage ? answer : null;
                        } else if (type == Stream) {
                            answer = answer instanceof StreamMessage ? answer : null;
                        }
                    }
                }
            }
        }

        if (answer == null) {
            if (cause != null) {
                // an exception occurred so send it as response
                LOG.debug("Will create JmsMessage with caused exception: {}", cause.getMessage(), cause);
                // create jms message containing the caused exception
                answer = createJmsMessage(cause, session);
            } else {
                org.apache.camel.util.ObjectHelper.notNull(camelMessage, "message");
                // create regular jms message using the camel message body
                answer = createJmsMessage(exchange, camelMessage, session, exchange.getContext());
                appendJmsProperties(answer, exchange, camelMessage);
            }
        }

        if (answer != null && messageCreatedStrategy != null) {
            messageCreatedStrategy.onMessageCreated(answer, session, exchange, null);
        }
        return answer;
    }

    /**
     * Appends the JMS headers from the Camel {@link JmsMessage}
     */
    public void appendJmsProperties(Message jmsMessage, Exchange exchange) throws JMSException {
        appendJmsProperties(jmsMessage, exchange, exchange.getIn());
    }

    /**
     * Appends the JMS headers from the Camel {@link JmsMessage}
     */
    public void appendJmsProperties(Message jmsMessage, Exchange exchange, org.apache.camel.Message in) throws JMSException {
        Set<Map.Entry<String, Object>> entries = in.getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            appendJmsProperty(jmsMessage, exchange, headerName, headerValue);
        }
    }

    public void appendJmsProperty(
            Message jmsMessage, Exchange exchange,
            String headerName, Object headerValue)
            throws JMSException {
        if (isStandardJMSHeader(headerName)) {
            if (headerName.equals(JmsConstants.JMS_HEADER_CORRELATION_ID)
                    && (endpoint == null || !endpoint.isUseMessageIDAsCorrelationID())) {
                jmsMessage.setJMSCorrelationID(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals(JmsConstants.JMS_HEADER_REPLY_TO) && headerValue != null) {
                if (headerValue instanceof String) {
                    // if the value is a String we must normalize it first, and must include the prefix
                    // as ActiveMQ requires that when converting the String to a jakarta.jms.Destination type
                    headerValue = normalizeDestinationName((String) headerValue, true);
                }
                Destination replyTo = ExchangeHelper.convertToType(exchange, Destination.class, headerValue);
                JmsMessageHelper.setJMSReplyTo(jmsMessage, replyTo);
            } else if (headerName.equals(JmsConstants.JMS_HEADER_TYPE)) {
                jmsMessage.setJMSType(ExchangeHelper.convertToType(exchange, String.class, headerValue));
            } else if (headerName.equals(JmsConstants.JMS_HEADER_PRIORITY)) {
                jmsMessage.setJMSPriority(ExchangeHelper.convertToType(exchange, Integer.class, headerValue));
            } else if (headerName.equals(JmsConstants.JMS_HEADER_DELIVERY_MODE)) {
                JmsMessageHelper.setJMSDeliveryMode(exchange, jmsMessage, headerValue);
            } else if (headerName.equals(JmsConstants.JMS_HEADER_EXPIRATION)) {
                jmsMessage.setJMSExpiration(ExchangeHelper.convertToType(exchange, Long.class, headerValue));
            } else {
                // The following properties are set by the MessageProducer:
                // JMSDestination
                // The following are set on the underlying JMS provider:
                // JMSMessageID, JMSTimestamp, JMSRedelivered
                // log at trace level to not spam log
                LOG.trace("Ignoring JMS header: {} with value: {}", headerName, headerValue);
            }
        } else if (shouldOutputHeader(headerName, headerValue, exchange)) {
            // only primitive headers and strings is allowed as properties
            // see message properties: http://java.sun.com/j2ee/1.4/docs/api/javax/jms/Message.html
            Object value = getValidJMSHeaderValue(headerName, headerValue);
            // if the value was null, then it may be allowed as an additional header
            if (value == null && endpoint != null && endpoint.getConfiguration().getAllowAdditionalHeaders() != null) {
                Iterator it = ObjectHelper.createIterator(endpoint.getConfiguration().getAllowAdditionalHeaders());
                while (it.hasNext()) {
                    String pattern = (String) it.next();
                    if (PatternHelper.matchPattern(headerName, pattern)) {
                        LOG.debug(
                                "Header {} allowed as additional header despite not being valid according to the JMS specification",
                                headerName);
                        value = headerValue;
                        break;
                    }
                }
            }
            if (value != null) {
                // must encode to safe JMS header name before setting property on jmsMessage
                String key = jmsKeyFormatStrategy.encodeKey(headerName);
                // set the property
                JmsMessageHelper.setProperty(jmsMessage, key, value);
            } else if (LOG.isDebugEnabled()) {
                // okay the value is not a primitive or string so we cannot sent it over the wire
                LOG.debug("Ignoring non primitive header: {} of class: {} with value: {}",
                        headerName, headerValue.getClass().getName(), headerValue);
            }
        }
    }

    /**
     * Is the given header a standard JMS header
     *
     * @param  headerName the header name
     * @return            <tt>true</tt> if its a standard JMS header
     */
    protected boolean isStandardJMSHeader(String headerName) {
        if (!headerName.startsWith("JMS")) {
            return false;
        }
        if (headerName.startsWith("JMSX")) {
            return false;
        }
        // vendors will use JMS_XXX as their special headers (where XXX is vendor name, such as JMS_IBM)
        if (headerName.startsWith("JMS_")) {
            return false;
        }

        // the 4th char must be a letter to be a standard JMS header
        if (headerName.length() > 3) {
            char fourth = headerName.charAt(3);
            if (Character.isLetter(fourth)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Strategy to test if the given header is valid according to the JMS spec to be set as a property on the JMS
     * message.
     * <p/>
     * This default implementation will allow:
     * <ul>
     * <li>any primitives and their counter Objects (Integer, Double etc.)</li>
     * <li>String and any other literals, Character, CharSequence</li>
     * <li>Boolean</li>
     * <li>Number</li>
     * <li>java.math.BigInteger</li>
     * <li>java.math.BigDecimal</li>
     * <li>java.util.Date</li>
     * </ul>
     *
     * @param  headerName  the header name
     * @param  headerValue the header value
     * @return             the value to use, <tt>null</tt> to ignore this header
     */
    protected Object getValidJMSHeaderValue(String headerName, Object headerValue) {
        if (headerValue instanceof String) {
            return headerValue;
        } else if (headerValue instanceof BigInteger) {
            return headerValue.toString();
        } else if (headerValue instanceof BigDecimal) {
            return headerValue.toString();
        } else if (headerValue instanceof Number) {
            return headerValue;
        } else if (headerValue instanceof Character) {
            return headerValue;
        } else if (headerValue instanceof CharSequence) {
            return headerValue.toString();
        } else if (headerValue instanceof Boolean) {
            return headerValue;
        } else if (headerValue instanceof Date) {
            if (this.endpoint.getConfiguration().isFormatDateHeadersToIso8601()) {
                return ZonedDateTime.ofInstant(((Date) headerValue).toInstant(), ZoneOffset.UTC).toString();
            } else {
                return headerValue.toString();
            }
        }
        return null;
    }

    protected Message createJmsMessage(Exception cause, Session session) throws JMSException {
        LOG.trace("Using JmsMessageType: {}", Object);
        Message answer = session.createObjectMessage(cause);
        // ensure default delivery mode is used by default
        answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
        return answer;
    }

    protected Message createJmsMessage(
            Exchange exchange, org.apache.camel.Message camelMessage, Session session, CamelContext context)
            throws JMSException {
        return createJmsMessage(exchange, camelMessage.getBody(), camelMessage.getHeaders(), session, context);
    }

    protected Message createJmsMessage(
            Exchange exchange, Object body, Map<String, Object> headers, Session session, CamelContext context)
            throws JMSException {
        JmsMessageType type;

        // special for transferExchange
        if (endpoint != null && endpoint.isTransferExchange()) {
            LOG.trace("Option transferExchange=true so we use JmsMessageType: Object");
            Serializable holder = DefaultExchangeHolder.marshal(exchange, true, endpoint.isAllowSerializedHeaders(), false);
            Message answer = session.createObjectMessage(holder);
            // ensure default delivery mode is used by default
            answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
            return answer;
        }

        // use a custom message converter
        if (endpoint != null && endpoint.getMessageConverter() != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Creating JmsMessage using a custom MessageConverter: {} with body: {}",
                        endpoint.getMessageConverter(), body);
            }
            return endpoint.getMessageConverter().toMessage(body, session);
        }

        // check if header have a type set, if so we force to use it
        if (headers.containsKey(JmsConstants.JMS_MESSAGE_TYPE)) {
            type = context.getTypeConverter().convertTo(JmsMessageType.class, headers.get(JmsConstants.JMS_MESSAGE_TYPE));
        } else if (endpoint != null && endpoint.getConfiguration().getJmsMessageType() != null) {
            // force a specific type from the endpoint configuration
            type = endpoint.getConfiguration().getJmsMessageType();
        } else {
            type = getJMSMessageTypeForBody(exchange, body);
        }

        // create the JmsMessage based on the type
        if (type != null) {
            if (body == null && endpoint != null && !endpoint.getConfiguration().isAllowNullBody()) {
                throw new JMSException("Cannot send message as message body is null, and option allowNullBody is false.");
            }
            LOG.trace("Using JmsMessageType: {}", type);
            Message answer = createJmsMessageForType(exchange, body, session, context, type);
            // ensure default delivery mode is used by default
            answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
            return answer;
        }

        // check for null body
        if (body == null && endpoint != null && !endpoint.getConfiguration().isAllowNullBody()) {
            throw new JMSException("Cannot send message as message body is null, and option allowNullBody is false.");
        }

        // warn if the body could not be mapped
        if (body != null && LOG.isWarnEnabled()) {
            LOG.warn("Cannot determine specific JmsMessage type to use from body class."
                     + " Will use generic JmsMessage."
                     + " Body class: {}"
                     + ". If you want to send a POJO then your class might need to implement java.io.Serializable"
                     + ", or you can force a specific type by setting the jmsMessageType option on the JMS endpoint.",
                    org.apache.camel.util.ObjectHelper.classCanonicalName(body));
        }

        // return a default message
        Message answer = session.createMessage();
        // ensure default delivery mode is used by default
        answer.setJMSDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
        return answer;
    }

    /**
     * Return the {@link JmsMessageType}
     *
     * @return type or null if no mapping was possible
     */
    protected JmsMessageType getJMSMessageTypeForBody(
            Exchange exchange, Object body) {
        boolean streamingEnabled = endpoint.getConfiguration().isStreamMessageTypeEnabled();

        JmsMessageType type = null;
        // let body determine the type
        if (body instanceof Node || body instanceof String) {
            type = Text;
        } else if (body instanceof byte[] || body instanceof ByteBuffer) {
            type = Bytes;
        } else if (body instanceof WrappedFile || body instanceof File || body instanceof Reader
                || body instanceof InputStream || body instanceof StreamCache) {
            type = streamingEnabled ? Stream : Bytes;
        } else if (body instanceof Map) {
            type = Map;
        } else if (body instanceof Serializable) {
            type = Object;
        } else if (exchange.getContext().getTypeConverter().tryConvertTo(File.class, body) != null
                || exchange.getContext().getTypeConverter().tryConvertTo(InputStream.class, body) != null) {
            type = streamingEnabled ? Stream : Bytes;
        }

        if (type == Stream) {
            boolean artemis = endpoint.isArtemisStreamingEnabled();
            if (artemis) {
                // if running ActiveMQ Artemis then it has optimised streaming mode
                // that requires using byte messages instead of stream, so we have to enforce as bytes
                type = Bytes;
            }
        }

        return type;
    }

    /**
     *
     * Create the {@link Message}
     *
     * @return jmsMessage or null if the mapping was not successfully
     */
    protected Message createJmsMessageForType(
            Exchange exchange, Object body, Session session, CamelContext context,
            JmsMessageType type)
            throws JMSException {
        switch (type) {
            case Text: {
                TextMessage message = session.createTextMessage();
                if (body != null) {
                    String payload = context.getTypeConverter().convertTo(String.class, exchange, body);
                    message.setText(payload);
                }
                return message;
            }
            case Bytes: {
                BytesMessage message = session.createBytesMessage();
                if (body != null) {
                    try {
                        if (endpoint.isArtemisStreamingEnabled()) {
                            LOG.trace("Optimised for Artemis: Streaming payload in BytesMessage");
                            InputStream is = context.getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
                            message.setObjectProperty("JMS_AMQ_InputStream", is);
                            LOG.trace("Optimised for Artemis: Finished streaming payload in BytesMessage");
                        } else {
                            byte[] payload = context.getTypeConverter().mandatoryConvertTo(byte[].class, exchange, body);
                            message.writeBytes(payload);
                        }
                    } catch (NoTypeConversionAvailableException e) {
                        // cannot convert to inputstream then thrown an exception to avoid sending a null message
                        JMSException cause = new MessageFormatException(e.getMessage());
                        cause.initCause(e);
                        throw cause;
                    }
                }
                return message;
            }
            case Map: {
                MapMessage message = session.createMapMessage();
                if (body != null) {
                    Map<?, ?> payload = context.getTypeConverter().convertTo(Map.class, exchange, body);
                    populateMapMessage(message, payload, context);
                }
                return message;
            }
            case Object: {
                ObjectMessage message = session.createObjectMessage();
                if (body != null) {
                    try {
                        Serializable payload
                                = context.getTypeConverter().mandatoryConvertTo(Serializable.class, exchange, body);
                        message.setObject(payload);
                    } catch (NoTypeConversionAvailableException e) {
                        // cannot convert to serializable then thrown an exception to avoid sending a null message
                        JMSException cause = new MessageFormatException(e.getMessage());
                        cause.initCause(e);
                        throw cause;
                    }
                }
                return message;
            }
            case Stream: {
                StreamMessage message = session.createStreamMessage();
                if (body != null) {
                    long size = 0;
                    InputStream is = null;
                    try {
                        is = context.getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
                        LOG.trace("Writing payload in StreamMessage");
                        // assume streaming is bigger payload so use same buffer size as the file component
                        byte[] buffer = new byte[FileUtil.BUFFER_SIZE];
                        int len = 0;
                        int count = 0;
                        while (len >= 0) {
                            count++;
                            len = is.read(buffer);
                            if (len >= 0) {
                                size += len;
                                LOG.trace("Writing payload chunk {} as bytes in StreamMessage", count);
                                message.writeBytes(buffer, 0, len);
                            }
                        }
                        LOG.trace("Finished writing payload (size {}) as bytes in StreamMessage", size);
                    } catch (NoTypeConversionAvailableException | IOException e) {
                        // cannot convert to inputstream then thrown an exception to avoid sending a null message
                        JMSException cause = new MessageFormatException(e.getMessage());
                        cause.initCause(e);
                        throw cause;
                    } finally {
                        IOHelper.close(is);
                    }

                }
                return message;
            }
            default:
                break;
        }
        return null;
    }

    /**
     * Populates a {@link MapMessage} from a {@link Map} instance.
     */
    protected void populateMapMessage(MapMessage message, Map<?, ?> map, CamelContext context)
            throws JMSException {
        for (Entry<?, ?> entry : map.entrySet()) {
            String keyString = CamelContextHelper.convertTo(context, String.class, entry.getKey());
            if (keyString != null) {
                message.setObject(keyString, entry.getValue());
            }
        }
    }

    /**
     * Extracts a {@link Map} from a {@link MapMessage}
     */
    public Map<String, Object> createMapFromMapMessage(MapMessage message) throws JMSException {
        Map<String, Object> answer = new HashMap<>();
        Enumeration<?> names = message.getMapNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement().toString();
            Object value = message.getObject(name);
            answer.put(name, value);
        }
        return answer;
    }

    /**
     * Strategy to allow filtering of headers which are put on the JMS message
     * <p/>
     * <b>Note</b>: Currently only supports sending java identifiers as keys
     */
    protected boolean shouldOutputHeader(
            String headerName,
            Object headerValue, Exchange exchange) {
        return headerFilterStrategy == null
                || !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }

}
