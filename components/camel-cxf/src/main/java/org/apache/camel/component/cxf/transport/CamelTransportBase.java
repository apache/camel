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
package org.apache.camel.component.cxf.transport;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelTemplate;
import org.apache.camel.Exchange;
import org.apache.cxf.Bus;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;

/**
 * @version $Revision$
 */
public class CamelTransportBase {
    CamelTemplate<Exchange> template;
    Bus bus;
    EndpointInfo endpointInfo;
    private String replyDestination;
    private final CamelContext camelContext;

    public CamelTransportBase(CamelContext camelContext, Bus bus, EndpointInfo endpointInfo, boolean b, String baseBeanNameSuffix) {
        this.camelContext = camelContext;
        this.bus = bus;
        this.endpointInfo = endpointInfo;
        this.template = new CamelTemplate<Exchange>(camelContext);
    }

    public void populateIncomingContext(Exchange exchange, MessageImpl inMessage, String camelServerRequestHeaders) {

    }

    public String getReplyDestination() {
        return replyDestination;
    }

    public void setMessageProperties(Message inMessage, Exchange reply) {

    }

    public void close() {
        if (template != null) {
            try {
                template.stop();
            } catch (Exception e) {
                // do nothing?
                // TODO
            }
        }
    }

    /**
     * Populates a Camel exchange with a payload
     * 
     * @param payload the message payload, expected to be either of type String
     *                or byte[] depending on payload type
     * @param replyTo the ReplyTo destination if any
     * @param exchange the underlying exchange to marshal to
     */
    protected void marshal(Object payload, String replyTo, Exchange exchange) {
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
     * protected CamelMessageHeadersType
     * populateIncomingContext(javax.camel.Message message,
     * org.apache.cxf.message.Message inMessage, String headerType) throws
     * CamelException { CamelMessageHeadersType headers = null; headers =
     * (CamelMessageHeadersType)inMessage.get(headerType); if (headers == null) {
     * headers = new CamelMessageHeadersType(); inMessage.put(headerType,
     * headers); }
     * headers.setCamelCorrelationID(message.getCamelCorrelationID());
     * headers.setCamelDeliveryMode(new
     * Integer(message.getCamelDeliveryMode())); headers.setCamelExpiration(new
     * Long(message.getCamelExpiration()));
     * headers.setCamelMessageID(message.getCamelMessageID());
     * headers.setCamelPriority(new Integer(message.getCamelPriority()));
     * headers.setCamelRedelivered(Boolean.valueOf(message.getCamelRedelivered()));
     * headers.setCamelTimeStamp(new Long(message.getCamelTimestamp()));
     * headers.setCamelType(message.getCamelType()); List<CamelPropertyType>
     * props = headers.getProperty(); Enumeration enm =
     * message.getPropertyNames(); while (enm.hasMoreElements()) { String name =
     * (String)enm.nextElement(); String val = message.getStringProperty(name);
     * CamelPropertyType prop = new CamelPropertyType(); prop.setName(name);
     * prop.setValue(val); props.add(prop); } return headers; } protected int
     * getCamelDeliveryMode(CamelMessageHeadersType headers) { int deliveryMode =
     * Message.DEFAULT_DELIVERY_MODE; if (headers != null &&
     * headers.isSetCamelDeliveryMode()) { deliveryMode =
     * headers.getCamelDeliveryMode(); } return deliveryMode; } protected int
     * getCamelPriority(CamelMessageHeadersType headers) { int priority =
     * Message.DEFAULT_PRIORITY; if (headers != null &&
     * headers.isSetCamelPriority()) { priority = headers.getCamelPriority(); }
     * return priority; } protected long getTimeToLive(CamelMessageHeadersType
     * headers) { long ttl = -1; if (headers != null &&
     * headers.isSetTimeToLive()) { ttl = headers.getTimeToLive(); } return ttl; }
     * protected String getCorrelationId(CamelMessageHeadersType headers) {
     * String correlationId = null; if (headers != null &&
     * headers.isSetCamelCorrelationID()) { correlationId =
     * headers.getCamelCorrelationID(); } return correlationId; } protected
     * String getAddrUriFromCamelAddrPolicy() { AddressType camelAddressPolicy =
     * transport.getCamelAddress(); return "camel:" +
     * camelAddressPolicy.getJndiConnectionFactoryName() + "#" +
     * camelAddressPolicy.getJndiDestinationName(); } protected String
     * getReplyTotAddrUriFromCamelAddrPolicy() { AddressType camelAddressPolicy =
     * transport.getCamelAddress(); return "camel:" +
     * camelAddressPolicy.getJndiConnectionFactoryName() + "#" +
     * camelAddressPolicy.getJndiReplyDestinationName(); } protected boolean
     * isDestinationStyleQueue() { return CamelConstants.CAMEL_QUEUE.equals(
     * transport.getCamelAddress().getDestinationStyle().value()); }
     */
}
