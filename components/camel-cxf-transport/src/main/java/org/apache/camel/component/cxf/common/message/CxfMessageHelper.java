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
package org.apache.camel.component.cxf.common.message;

import java.io.InputStream;

import org.apache.camel.component.cxf.common.header.CxfHeaderHelper;
import org.apache.camel.component.cxf.transport.CamelTransportConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;

public final class CxfMessageHelper {

    private CxfMessageHelper() {
        //Helper class
    }
    
    public static org.apache.cxf.message.Message getCxfInMessage(HeaderFilterStrategy headerFilterStrategy,
                                                                 org.apache.camel.Exchange exchange,
                                                                 boolean isClient) {
        MessageImpl answer = new MessageImpl();
        org.apache.cxf.message.Exchange cxfExchange = exchange
            .getProperty(CamelTransportConstants.CXF_EXCHANGE, org.apache.cxf.message.Exchange.class);
        org.apache.camel.Message message;
        if (isClient && exchange.hasOut()) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }
        assert message != null;
        if (cxfExchange == null) {
            cxfExchange = new ExchangeImpl();
            exchange.setProperty(CamelTransportConstants.CXF_EXCHANGE, cxfExchange);
        }

        CxfHeaderHelper.propagateCamelToCxf(headerFilterStrategy, message.getHeaders(), answer, exchange);

        // body can be empty in case of GET etc.
        InputStream body = message.getBody(InputStream.class);
        if (body != null) {
            answer.setContent(InputStream.class, body);
        } else if (message.getBody() != null) {
            // fallback and set the body as what it is
            answer.setContent(Object.class, body);
        }

        answer.putAll(message.getHeaders());
        answer.setExchange(cxfExchange);
        cxfExchange.setInMessage(answer);
        return answer;
    }

}
