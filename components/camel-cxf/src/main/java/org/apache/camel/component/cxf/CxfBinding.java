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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxws.support.ContextPropertiesMapping;
import org.apache.cxf.message.Message;

/**
 * The binding/mapping of Camel messages to Apache CXF and back again
 *
 * @version $Revision$
 */
public final class CxfBinding {
    private CxfBinding() {
        // Helper class
    }
    public static Object extractBodyFromCxf(CxfExchange exchange, Message message) {
        // TODO how do we choose a format?
        return getBody(message);
    }

    protected static Object getBody(Message message) {
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

    public static Message createCxfMessage(CxfExchange exchange) {
        Message answer = exchange.getInMessage();
        CxfMessage in = exchange.getIn();
        // Check the body if the POJO parameter list first
        Object body = in.getBody(List.class);
        if (body instanceof List) {
            // just set the operation's parameter
            answer.setContent(List.class, body);
            // just set the method name
            answer.put(CxfConstants.OPERATION_NAME, (String)in.getHeader(CxfConstants.OPERATION_NAME));
            answer.put(CxfConstants.OPERATION_NAMESPACE, (String)in.getHeader(CxfConstants.OPERATION_NAMESPACE));
        } else {
            // CXF uses StAX which is based on the stream API to parse the XML,
            // so the CXF transport is also based on the stream API.
            // And the interceptors are also based on the stream API,
            // so let's use an InputStream to host the CXF on wire message.
            body = in.getBody(InputStream.class);
            if (body instanceof InputStream) {
                answer.setContent(InputStream.class, body);
            }
        }
        return answer;
    }

    // Store
    public static void storeCxfResponse(CxfExchange exchange, Message response) {
        // no need to process headers as we use the CXF message
        CxfMessage out = exchange.getOut();
        if (response != null) {
            out.setMessage(response);
            DataFormat dataFormat = (DataFormat) exchange.getProperty(CxfExchange.DATA_FORMAT);
            if (dataFormat.equals(DataFormat.MESSAGE)) {
                out.setBody(response.getContent(InputStream.class));
            }
            if (dataFormat.equals(DataFormat.PAYLOAD)) {
                out.setBody(response);
            }
        }
    }

    // Copy the Camel message to CXF message
    public static void copyMessage(org.apache.camel.Message camelMessage, org.apache.cxf.message.Message cxfMessage) {
        InputStream is = camelMessage.getBody(InputStream.class);
        if (is != null) {
            cxfMessage.setContent(InputStream.class, is);
        } else {
            Object result = camelMessage.getBody();
            if (result != null) {
                if (result instanceof InputStream) {
                    cxfMessage.setContent(InputStream.class, result);
                } else {
                    cxfMessage.setContent(result.getClass(), result);
                }
            }
        }
    }

    public static void storeCXfResponseContext(Message response, Map<String, Object> context) {
        if (context != null) {
            ContextPropertiesMapping.mapResponsefromCxf2Jaxws(context);
            response.put(Client.RESPONSE_CONTEXT, context);
        }
    }

    public static void storeCxfResponse(CxfExchange exchange, Object response) {
        CxfMessage out = exchange.getOut();
        if (response != null) {
            out.setBody(response);
        }
    }

    public static void storeCxfFault(CxfExchange exchange, Message message) {
        CxfMessage fault = exchange.getFault();
        if (fault != null) {
            fault.setBody(getBody(message));
        }
    }


    public static Map<String, Object> propogateContext(Message message, Map<String, Object> context) {
        Map<String, Object> requestContext = CastUtils.cast((Map)message.get(Client.REQUEST_CONTEXT));
        Map<String, Object> responseContext = CastUtils.cast((Map)message.get(Client.RESPONSE_CONTEXT));
        // TODO map the JAXWS properties to cxf
        if (requestContext != null) {
            ContextPropertiesMapping.mapRequestfromJaxws2Cxf(requestContext);
        }

        if (responseContext == null) {
            responseContext = new HashMap<String, Object>();
        } else {
            // clear the response context
            responseContext.clear();
        }
        context.put(Client.REQUEST_CONTEXT, requestContext);
        context.put(Client.RESPONSE_CONTEXT, responseContext);
        return responseContext;

    }
}
