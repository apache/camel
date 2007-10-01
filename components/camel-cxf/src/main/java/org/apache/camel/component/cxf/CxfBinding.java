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

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;

import java.io.InputStream;
import java.util.List;
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
        if (contentFormats != null) {
            for (Class<?> contentFormat : contentFormats) {            
                Object answer = message.getContent(contentFormat);
                if (answer != null) {
                    return answer;
                }
            }
        }    
        return null;
    }

    public Message createCxfMessage(CxfExchange exchange) {
        Message answer = exchange.getInMessage();

        // CXF uses the stax which is based on the stream API to parser the XML, so
        // the CXF transport is also based on the stream API.
        // And the interceptors are also based on the stream API,
        // so lets use an InputStream to host the CXF on wire message.

        CxfMessage in = exchange.getIn();
        Object body = in.getBody(InputStream.class);
        if (body == null) {
            body = in.getBody();
        }
        if (body instanceof InputStream) {
        	answer.setContent(InputStream.class, body);
                // we need copy context 
        } else if (body instanceof List) {
        	//just set the operation's parament
        	answer.setContent(List.class, body);
                //just set the method name
                answer.setContent(String.class, in.getHeader(CxfConstants.OPERATION_NAME));
        }
        
        
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
    
    public void storeCxfFault(CxfExchange exchange, Message message) {
        CxfMessage fault = exchange.getFault();
        if (fault != null) {
            fault.setBody(getBody(message));
        }
    }
}
