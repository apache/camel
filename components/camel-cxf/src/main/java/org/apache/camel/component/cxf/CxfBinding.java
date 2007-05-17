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
package org.apache.camel.component.cxf;

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;

import java.io.InputStream;
import java.util.Set;

/**
 * The binding of how Camel messages get mapped to Apache CXF and back again
 *
 * @version $Revision$
 */
public class CxfBinding {

    public Object extractBodyFromCxf(CxfExchange exchange, Message message) {
        //  TODO how do we choose a format?
        return getBody(message);
    }

    protected Object getBody(Message message) {
        Set<Class<?>> contentFormats = message.getContentFormats();
        for (Class<?> contentFormat : contentFormats) {
            Object answer = message.getContent(contentFormat);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    public MessageImpl createCxfMessage(CxfExchange exchange) {
        MessageImpl answer = (MessageImpl) exchange.getInMessage();

        // TODO is InputStream the best type to give to CXF?
        CxfMessage in = exchange.getIn();
        Object body = in.getBody(InputStream.class);
        if (body == null) {
            body = in.getBody();
        }
        answer.setContent(InputStream.class, body);

        // no need to process headers as we reuse the CXF message
        /*
        // set the headers
        Set<Map.Entry<String, Object>> entries = in.getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            answer.put(entry.getKey(), entry.getValue());
        }
        */
        return answer;
    }

    public void storeCxfResponse(CxfExchange exchange, Message response) {
        // no need to process headers as we use the CXF message
        CxfMessage out = exchange.getOut();
        if (response != null) {
            out.setMessage(response);
            out.setBody(getBody(response));
        }
    }

    public void storeCxfResponse(CxfExchange exchange, Object response) {
        CxfMessage out = exchange.getOut();
        if (response != null) {
            out.setBody(response);
        }
    }
}
