/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.transport;

import org.apache.camel.Exchange;
import org.apache.camel.CamelContext;
import org.apache.camel.util.CamelClient;
import org.apache.cxf.Bus;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;

/**
 * @version $Revision$
 */
public class CamelTransportBase {
    private String replyDestination;
    CamelClient<Exchange> client;
    private final CamelContext camelContext;
    Bus bus;
    EndpointInfo endpointInfo;

    public CamelTransportBase(CamelContext camelContext, Bus bus, EndpointInfo endpointInfo, boolean b, String baseBeanNameSuffix) {
        this.camelContext = camelContext;
        this.bus = bus;
        this.endpointInfo = endpointInfo;
        this.client = new CamelClient<Exchange>(camelContext);
    }

    public void populateIncomingContext(Exchange exchange, MessageImpl inMessage, String jmsServerRequestHeaders) {

    }


    public String getReplyDestination() {
        return replyDestination;
    }

    public void setMessageProperties(Message inMessage, Exchange reply) {

    }

    public void close() {
        if (client != null) {
            try {
                client.stop();
            }
            catch (Exception e) {
                // do nothing?
                // TODO
            }
        }
    }

    /**
     * Populates a Camel exchange with a payload
     *
     * @param payload the message payload, expected to be either of type
     * String or byte[] depending on payload type
     * @param replyTo the ReplyTo destination if any
     * @param exchange the underlying exchange to marshal to
     */
    protected void marshal(Object payload, String replyTo,   Exchange exchange) {
        org.apache.camel.Message message = exchange.getIn();
        message.setBody(payload);
        if (replyTo != null) {
            message.setHeader(CamelConstants.CAMEL_CORRELATION_ID, replyTo);
        }

    }

    /**
     * Unmarshal the payload of an incoming message.
     */
    public byte[] unmarshal(Exchange exchange) {
        return exchange.getIn().getBody(byte[].class);
    }

    /*
    protected JMSMessageHeadersType populateIncomingContext(javax.jms.Message message,
                                                            org.apache.cxf.message.Message inMessage,
                                                     String headerType)  throws JMSException {
        JMSMessageHeadersType headers = null;

        headers = (JMSMessageHeadersType)inMessage.get(headerType);

        if (headers == null) {
            headers = new JMSMessageHeadersType();
            inMessage.put(headerType, headers);
        }

        headers.setJMSCorrelationID(message.getJMSCorrelationID());
        headers.setJMSDeliveryMode(new Integer(message.getJMSDeliveryMode()));
        headers.setJMSExpiration(new Long(message.getJMSExpiration()));
        headers.setJMSMessageID(message.getJMSMessageID());
        headers.setJMSPriority(new Integer(message.getJMSPriority()));
        headers.setJMSRedelivered(Boolean.valueOf(message.getJMSRedelivered()));
        headers.setJMSTimeStamp(new Long(message.getJMSTimestamp()));
        headers.setJMSType(message.getJMSType());

        List<JMSPropertyType> props = headers.getProperty();
        Enumeration enm = message.getPropertyNames();
        while (enm.hasMoreElements()) {
            String name = (String)enm.nextElement();
            String val = message.getStringProperty(name);
            JMSPropertyType prop = new JMSPropertyType();
            prop.setName(name);
            prop.setValue(val);
            props.add(prop);
        }

        return headers;
    }

    protected int getJMSDeliveryMode(JMSMessageHeadersType headers) {
        int deliveryMode = Message.DEFAULT_DELIVERY_MODE;

        if (headers != null && headers.isSetJMSDeliveryMode()) {
            deliveryMode = headers.getJMSDeliveryMode();
        }
        return deliveryMode;
    }

    protected int getJMSPriority(JMSMessageHeadersType headers) {
        int priority = Message.DEFAULT_PRIORITY;
        if (headers != null && headers.isSetJMSPriority()) {
            priority = headers.getJMSPriority();
        }
        return priority;
    }

    protected long getTimeToLive(JMSMessageHeadersType headers) {
        long ttl = -1;
        if (headers != null && headers.isSetTimeToLive()) {
            ttl = headers.getTimeToLive();
        }
        return ttl;
    }

    protected String getCorrelationId(JMSMessageHeadersType headers) {
        String correlationId  = null;
        if (headers != null
            && headers.isSetJMSCorrelationID()) {
            correlationId = headers.getJMSCorrelationID();
        }
        return correlationId;
    }


    protected String getAddrUriFromJMSAddrPolicy() {
        AddressType jmsAddressPolicy = transport.getJMSAddress();
        return "jms:" + jmsAddressPolicy.getJndiConnectionFactoryName()
                        + "#"
                        + jmsAddressPolicy.getJndiDestinationName();
    }

    protected String getReplyTotAddrUriFromJMSAddrPolicy() {
        AddressType jmsAddressPolicy = transport.getJMSAddress();
        return "jms:"
                        + jmsAddressPolicy.getJndiConnectionFactoryName()
                        + "#"
                        + jmsAddressPolicy.getJndiReplyDestinationName();
    }

    protected boolean isDestinationStyleQueue() {
        return JMSConstants.CAMEL_QUEUE.equals(
            transport.getJMSAddress().getDestinationStyle().value());
    }
    */
}
