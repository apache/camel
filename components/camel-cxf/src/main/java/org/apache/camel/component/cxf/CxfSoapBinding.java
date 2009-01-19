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
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.component.cxf.util.CxfHeaderHelper;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;

public final class CxfSoapBinding {
    private static final Log LOG = LogFactory.getLog(CxfSoapBinding.class);

    private CxfSoapBinding() {

    }
    
    /**
     * @deprecated  Please use {@link #getCxfInMessage(HeaderFilterStrategy, org.apache.camel.Exchange, boolean)}
     */
    public static org.apache.cxf.message.Message getCxfInMessage(org.apache.camel.Exchange exchange, boolean isClient) {
        return CxfSoapBinding.getCxfInMessage(new DefaultHeaderFilterStrategy(), exchange, isClient);
    }
    
    public static org.apache.cxf.message.Message getCxfInMessage(HeaderFilterStrategy headerFilterStrategy,
            org.apache.camel.Exchange exchange, boolean isClient) {
        MessageImpl answer = new MessageImpl();
        org.apache.cxf.message.Exchange cxfExchange = exchange.getProperty(CxfConstants.CXF_EXCHANGE,
                                                                        org.apache.cxf.message.Exchange.class);
        org.apache.camel.Message message = null;
        if (isClient) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }
        ObjectHelper.notNull(message, "message");
        if (cxfExchange == null) {
            cxfExchange = new ExchangeImpl();
            exchange.setProperty(CxfConstants.CXF_EXCHANGE, cxfExchange);
        }

        CxfHeaderHelper.propagateCamelToCxf(headerFilterStrategy, message.getHeaders(), answer);

        try {
            InputStream body = message.getBody(InputStream.class);
            answer.setContent(InputStream.class, body);
        } catch (NoTypeConversionAvailableException ex) {
            LOG.warn("Can't get right InputStream object here, the message body is " + message.getBody());
        }

        answer.putAll(message.getHeaders());
        answer.setExchange(cxfExchange);
        cxfExchange.setInMessage(answer);
        return answer;
    }

    /**
     * @deprecated Please use {@link #getCxfOutMessage(HeaderFilterStrategy, org.apache.camel.Exchange, boolean)}
     */
    public static org.apache.cxf.message.Message getCxfOutMessage(org.apache.camel.Exchange exchange, boolean isClient) {
        return CxfSoapBinding.getCxfOutMessage(new DefaultHeaderFilterStrategy(), exchange, isClient);
    }
    
    public static org.apache.cxf.message.Message getCxfOutMessage(HeaderFilterStrategy headerFilterStrategy,
            org.apache.camel.Exchange exchange, boolean isClient) {
        org.apache.cxf.message.Exchange cxfExchange = exchange.getProperty(CxfConstants.CXF_EXCHANGE, org.apache.cxf.message.Exchange.class);
        ObjectHelper.notNull(cxfExchange, "cxfExchange");
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

        CxfHeaderHelper.propagateCamelToCxf(headerFilterStrategy, message.getHeaders(), outMessage);

        // send the body back
        try {
            Source body = message.getBody(Source.class);
            outMessage.setContent(Source.class, body);
        } catch (NoTypeConversionAvailableException ex) {
            LOG.warn("Can't get right Source object here, the message body is " + message.getBody());
        }
        outMessage.putAll(message.getHeaders());
        return outMessage;
    }

    /**
     * @deprecated Please use {@link CxfHeaderHelper#propagateCxfToCamel(HeaderFilterStrategy, org.apache.cxf.message.Message, Map)}
     */
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
