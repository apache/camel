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
package org.apache.camel.component.mina;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Holder object for sending an exchange over the wire using the MINA ObjectSerializationCodecFactory codec.
 * This is configured using the <tt>transferExchange=true</tt> option for the TCP protocol.
 * <p/>
 * As opposed to normal usage of camel-mina where only the body part of the exchange is transfered, this holder
 * object serializes the following fields over the wire:
 * <ul>
 *     <li>in body</li>
 *     <li>out body</li>
 *     <li>in headers</li>
 *     <li>out headers</li>
 *     <li>fault body </li>
 *     <li>fault headers</li>
 *     <li>exchange properties</li>
 *     <li>exception</li>
 * </ul>
 * Any object that is not serializable will be skipped and Camel will log this at WARN level.
 *
 * @version $Revision$
 */
public class MinaPayloadHolder implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final transient Log LOG = LogFactory.getLog(MinaPayloadHolder.class);

    private Object inBody;
    private Object outBody;
    private Object faultBody;
    private Map<String, Object> inHeaders = new LinkedHashMap<String, Object>();
    private Map<String, Object> outHeaders = new LinkedHashMap<String, Object>();
    private Map<String, Object> properties = new LinkedHashMap<String, Object>();
    private Map<String, Object> faultHeaders = new LinkedHashMap<String, Object>();
    private Throwable exception;

    /**
     * Creates a payload object with the information from the given exchange.
     * Only marshal the Serializable object
     *
     * @param exchange     the exchange
     * @return the holder object with information copied form the exchange
     */
    public static MinaPayloadHolder marshal(Exchange exchange) {
        MinaPayloadHolder payload = new MinaPayloadHolder();

        payload.inBody = checkSerializableObject("in body", exchange.getIn().getBody());
        payload.inHeaders.putAll(checkMapSerializableObjects("in headers", exchange.getIn().getHeaders()));
        if (exchange.getOut(false) != null) {
            payload.outBody = checkSerializableObject("out body", exchange.getOut().getBody());
            payload.outHeaders.putAll(checkMapSerializableObjects("out headers", exchange.getOut().getHeaders()));
        }
        if (exchange.getFault(false) != null) {
            payload.faultBody = checkSerializableObject("fault body", exchange.getFault().getBody());
            payload.faultHeaders.putAll(checkMapSerializableObjects("fault headers", exchange.getFault().getHeaders()));
        }
        payload.properties.putAll(checkMapSerializableObjects("exchange properties", exchange.getProperties()));
        payload.exception = exchange.getException();

        return payload;
    }

    /**
     * Transfers the information from the payload to the exchange.
     *
     * @param exchange   the exchange to set values from the payload
     * @param payload    the payload with the values
     */
    public static void unmarshal(Exchange exchange, MinaPayloadHolder payload) {
        exchange.getIn().setBody(payload.inBody);
        exchange.getIn().setHeaders(payload.inHeaders);
        if (payload.outBody != null) {
            exchange.getOut().setBody(payload.outBody);
            exchange.getOut().setHeaders(payload.outHeaders);
        }
        if (payload.faultBody != null) {
            exchange.getFault().setBody(payload.faultBody);
            exchange.getFault().setHeaders(payload.faultHeaders);
        }
        for (String key : payload.properties.keySet()) {
            exchange.setProperty(key, payload.properties.get(key));
        }
        exchange.setException(payload.exception);
    }

    public String toString() {
        return "MinaPayloadHolder{" + "inBody=" + inBody + ", outBody=" + outBody + ", inHeaders="
               + inHeaders + ", outHeaders=" + outHeaders + ", faultBody=" + faultBody + ", faultHeaders="
               + faultHeaders + ", properties=" + properties + ", exception=" + exception + '}';
    }

    private static Object checkSerializableObject(String type, Object object) {
        if (object instanceof Serializable) {
            return object;
        } else {
            LOG.warn(type + " containig object " + object + " can not be serialized, it will be excluded by the MinaPayloadHolder");
            return null;
        }
    }

    private static Map<String, Object> checkMapSerializableObjects(String type, Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Serializable) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                LOG.warn(type + " containing object " + entry.getValue() + " of key " + entry.getKey()
                         + " can not be serialized, it will be excluded by the MinaPayloadHolder");
            }
        }

        return result;
    }

}
