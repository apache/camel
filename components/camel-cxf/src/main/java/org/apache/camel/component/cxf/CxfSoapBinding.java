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
import javax.xml.transform.Source;

import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.util.CxfHeaderHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;

public final class CxfSoapBinding {

    private CxfSoapBinding() {
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
        assert message != null;
        if (cxfExchange == null) {
            cxfExchange = new ExchangeImpl();
            exchange.setProperty(CxfConstants.CXF_EXCHANGE, cxfExchange);
        }

        CxfHeaderHelper.propagateCamelToCxf(headerFilterStrategy, message.getHeaders(), answer, exchange);

        InputStream body = null;
        try {
            body = message.getMandatoryBody(InputStream.class);
        } catch (InvalidPayloadException e) {
            throw new RuntimeCamelException(e);
        }
        answer.setContent(InputStream.class, body);

        answer.putAll(message.getHeaders());
        answer.setExchange(cxfExchange);
        cxfExchange.setInMessage(answer);
        return answer;
    }    
    
    public static org.apache.cxf.message.Message getCxfOutMessage(HeaderFilterStrategy headerFilterStrategy,
            org.apache.camel.Exchange exchange, boolean isClient) {
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

        CxfHeaderHelper.propagateCamelToCxf(headerFilterStrategy, message.getHeaders(), outMessage, exchange);

        // send the body back
        Source body = null;
        try {
            body = message.getMandatoryBody(Source.class);
        } catch (InvalidPayloadException e) {
            throw new RuntimeCamelException(e);
        }
        outMessage.setContent(Source.class, body);
        outMessage.putAll(message.getHeaders());
        return outMessage;
    }
}
