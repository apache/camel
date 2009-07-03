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

package org.apache.camel.component.cxf.jaxrs;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.impl.DefaultProducer;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.MessageContentsList;

/**
 * CxfRsProducer binds a Camel exchange to a CXF exchange, acts as a CXF 
 * JAXRS client, it will turn the normal Object invocation to a RESTful request
 * according to resource annotation.  Any response will be bound to Camel exchange. 
 */
public class CxfRsProducer extends DefaultProducer {
    JAXRSClientFactoryBean cfb;

    public CxfRsProducer(CxfRsEndpoint endpoint) {
        super(endpoint);
        cfb = endpoint.createJAXRSClientFactoryBean();
    }

    public void process(Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        Boolean httpClientAPI = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, Boolean.class);
        if (httpClientAPI != null && httpClientAPI.booleanValue()) {
            invokeHttpClient(exchange);
        } else {
            invokeProxyClient(exchange);            
        }
        
    }
    
    @SuppressWarnings("unchecked")
    protected void invokeHttpClient(Exchange exchange) {
        Message inMessage = exchange.getIn();       
        WebClient client = cfb.createWebClient();
        String httpMethod = inMessage.getHeader(Exchange.HTTP_METHOD, String.class); 
        Class responseClass = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, Class.class);        
        String path = inMessage.getHeader(Exchange.HTTP_PATH, String.class);
       
        if (path != null) {
            client.path(path);
        } 
        Object body = null;
        if (!"GET".equals(httpMethod)) {
            // need to check the request object
            body = checkRequestObject(inMessage.getBody());            
        }
        
        /*String acceptContentType = inMessage.getHeader(Exchange.ACCEPT_CONTENT_TYPE, String.class);
        if (acceptContentType != null) {            
            client.accept(acceptContentType);            
        }*/
        Object response = null;        
        if (responseClass == null) {
            response = client.invoke(httpMethod, body, InputStream.class);
        } else if (responseClass.equals(Response.class)) {
            response = client.invoke(httpMethod, body);
        } else {
            response = client.invoke(httpMethod, body, responseClass);
        }
       
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setBody(response);
        }
    }

    protected void invokeProxyClient(Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();
        Object[] varValues = inMessage.getHeader(CxfConstants.CAMEL_CXF_RS_VAR_VALUES, Object[].class);
        String methodName = inMessage.getHeader(CxfConstants.OPERATION_NAME, String.class);
        Client target = null;
        if (varValues == null) {
            target = cfb.create();
        } else {
            target = cfb.createWithValues(varValues);
        }    
        // find out the method which we want to invoke
        JAXRSServiceFactoryBean sfb = cfb.getServiceFactory();
        sfb.getResourceClasses();
        Object[] parameters = inMessage.getBody(Object[].class);
        // get the method
        Method method = findRightMethod(sfb.getResourceClasses(), methodName, getParameterTypes(parameters));
        // Will send out the message to
        // Need to deal with the sub resource class
        Object response = method.invoke(target, parameters);
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setBody(response);
        }
    }

    private Method findRightMethod(List<Class> resourceClasses, String methodName, Class[] parameterTypes) throws NoSuchMethodException {        
        Method answer = null;
        for (Class clazz : resourceClasses) {
            try {
                answer = clazz.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ex) {
                // keep looking 
            } catch (SecurityException ex) {
                // keep looking
            }
            if (answer != null) {
                return answer;
            }
        }
        throw new NoSuchMethodException("Can find the method " + methodName 
            + "withe these parameter " + arrayToString(parameterTypes));
    }
    
    private Object checkRequestObject(Object request) {
        if (request != null) {
            if (request instanceof MessageContentsList) {
                request = ((MessageContentsList)request).get(0);
            } else if (request instanceof List) {
                request = ((List)request).get(0);
            } else if (request.getClass().isArray()) {
                request = ((Object[])request)[0];
            }
        }
        return request;
    }
    
    private Class[] getParameterTypes(Object[] objects) {
        Class[] answer = new Class[objects.length];
        int i = 0;
        for (Object obj : objects) {
            answer[i] = obj.getClass();
            i++;
        }
        return answer;
    }
    
    private String arrayToString(Object[] array) {
        StringBuffer buffer = new StringBuffer("[");
        for (Object obj : array) {
            if (buffer.length() > 2) {
                buffer.append(",");
            }
            buffer.append(obj.toString());
        }
        buffer.append("]");
        return buffer.toString();
    }

}
