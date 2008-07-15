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
package org.apache.camel.component.cxf;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;

public final class CxfSoapBinding {
    private static final Log LOG = LogFactory.getLog(CxfSoapBinding.class);

    private CxfSoapBinding() {

    }

    public static org.apache.cxf.message.Message getCxfInMessage(org.apache.camel.Exchange exchange, boolean isClient) {
        MessageImpl answer = new MessageImpl();
        org.apache.cxf.message.Exchange cxfExchange = exchange.getProperty(CxfConstants.CXF_EXCHANGE,
                                                                        org.apache.cxf.message.Exchange.class);
        org.apache.camel.Message message = null;
        if (isClient) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }
        assert message != null;
        if (cxfExchange == null) {
            cxfExchange = new ExchangeImpl();
            exchange.setProperty(CxfConstants.CXF_EXCHANGE, cxfExchange);
        }

        Map<String, Object> headers = null;
        if (isClient) {
            headers = exchange.getOut().getHeaders();
        } else {
            headers = exchange.getIn().getHeaders();
        }

        answer.put(Message.PROTOCOL_HEADERS, getProtocolHeader(headers));

        Object body = message.getBody(InputStream.class);
        if (body instanceof InputStream) {
            answer.setContent(InputStream.class, body);
        } else {
            LOG.warn("Can't get right InputStream object here, the message body is " + message.getBody());
        }

        answer.putAll(message.getHeaders());
        answer.setExchange(cxfExchange);
        cxfExchange.setInMessage(answer);
        return answer;
    }

    public static org.apache.cxf.message.Message getCxfOutMessage(org.apache.camel.Exchange exchange, boolean isClient) {
        org.apache.cxf.message.Exchange cxfExchange = exchange.getProperty(CxfConstants.CXF_EXCHANGE, org.apache.cxf.message.Exchange.class);
        assert cxfExchange != null;
        org.apache.cxf.endpoint.Endpoint cxfEndpoint = cxfExchange.get(org.apache.cxf.endpoint.Endpoint.class);
        org.apache.cxf.message.Message outMessage = cxfEndpoint.getBinding().createMessage();
        outMessage.setExchange(cxfExchange);
        cxfExchange.setOutMessage(outMessage);
        org.apache.camel.Message message = null;
        if (isClient) {
            message = exchange.getIn();
        } else {
            message = exchange.getOut();
        }
        Map<String, Object> headers = null;
        if (isClient) {
            headers = exchange.getIn().getHeaders();
        } else {
            headers = exchange.getOut().getHeaders();
        }

        outMessage.put(Message.PROTOCOL_HEADERS, getProtocolHeader(headers));
        // send the body back
        Object body = message.getBody(Source.class);
        if (body instanceof Source) {
            outMessage.setContent(Source.class, body);
        } else {
            LOG.warn("Can't get right Source object here, the message body is " + message.getBody());
        }
        outMessage.putAll(message.getHeaders());
        return outMessage;
    }

    private static Map<String, List<String>> getProtocolHeader(Map<String, Object> headers) {
        Map<String, List<String>> protocolHeader = new HashMap<String, List<String>>();
        Iterator headersKeySetIterator = headers.keySet().iterator();
        while (headersKeySetIterator.hasNext()) {
            String key = (String)headersKeySetIterator.next();
            Object value = headers.get(key);
            if (value != null) {
                protocolHeader.put(key, Collections.singletonList(value.toString()));
            } else {
                protocolHeader.put(key, null);
            }
        }
        return protocolHeader;
    }

    public static void setProtocolHeader(Map<String, Object> headers, Map<String, List<String>> protocolHeader) {
        if (protocolHeader != null) {
            StringBuilder value = new StringBuilder(256);
            for (Map.Entry<String, List<String>> entry : protocolHeader.entrySet()) {
                value.setLength(0);
                boolean first = true;
                for (String s : entry.getValue()) {
                    if (!first) {
                        value.append("; ");
                    }
                    value.append(s);
                    first = false;
                }
                headers.put(entry.getKey(), value.toString());
            }
        }

    }


}
