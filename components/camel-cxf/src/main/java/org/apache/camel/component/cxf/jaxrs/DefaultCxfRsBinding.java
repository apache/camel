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

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.security.auth.Subject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cxf.common.header.CxfHeaderHelper;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.security.LoginSecurityContext;
import org.apache.cxf.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default strategy  to bind between Camel and CXF exchange for RESTful resources.
 *
 *
 * @version 
 */
public class DefaultCxfRsBinding implements CxfRsBinding, HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCxfRsBinding.class);
    
    private HeaderFilterStrategy headerFilterStrategy;
    
    public DefaultCxfRsBinding() {
    }

    public Object populateCxfRsResponseFromExchange(Exchange camelExchange,
                                                    org.apache.cxf.message.Exchange cxfExchange) throws Exception {
        // Need to check if the exchange has the exception
        if (camelExchange.isFailed() && camelExchange.getException() != null) {
            throw camelExchange.getException();
        }

        org.apache.camel.Message response;
        if (camelExchange.getPattern().isOutCapable()) {
            if (camelExchange.hasOut()) {
                response = camelExchange.getOut();
                LOG.trace("Get the response from the out message");
            } else {
                response = camelExchange.getIn();
                LOG.trace("Get the response from the in message as a fallback");
            }
        } else {
            response = camelExchange.getIn();
            LOG.trace("Get the response from the in message");
        }

        Object o = response.getBody();
        if (!(o instanceof Response)) {
            //not a JAX-RS Response object, we need to set the headers from the Camel values
            
            
            if (response.getHeader(org.apache.cxf.message.Message.PROTOCOL_HEADERS) != null) {
                Map<String, Object> headers = CastUtils.cast((Map<?, ?>)response.getHeader(org.apache.cxf.message.Message.PROTOCOL_HEADERS));
                if (!ObjectHelper.isEmpty(cxfExchange) && !ObjectHelper.isEmpty(cxfExchange.getOutMessage())) {
                    cxfExchange.getOutMessage().putIfAbsent(org.apache.cxf.message.Message.PROTOCOL_HEADERS, 
                                                        new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                }
                final Map<String, List<String>> cxfHeaders =
                    CastUtils.cast((Map<?, ?>) cxfExchange.getOutMessage().get(org.apache.cxf.message.Message.PROTOCOL_HEADERS));
                
                for (Map.Entry<String, Object> ent : headers.entrySet()) {
                    List<String> v;
                    if (ent.getValue() instanceof List) {
                        v = CastUtils.cast((List<?>)ent.getValue());
                    } else {
                        v = Arrays.asList(ent.getValue().toString());
                    }
                    cxfHeaders.put(ent.getKey(), v);
                }
            }
            
            
            if (response.getHeader(Exchange.HTTP_RESPONSE_CODE) != null && !cxfExchange.containsKey(org.apache.cxf.message.Message.RESPONSE_CODE)) {
                cxfExchange.put(org.apache.cxf.message.Message.RESPONSE_CODE, response.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
            }
            if (response.getHeader(Exchange.CONTENT_TYPE) != null && !cxfExchange.containsKey(org.apache.cxf.message.Message.CONTENT_TYPE)) {
                if (!ObjectHelper.isEmpty(cxfExchange) && !ObjectHelper.isEmpty(cxfExchange.getOutMessage())) {
                    cxfExchange.getOutMessage().putIfAbsent(org.apache.cxf.message.Message.PROTOCOL_HEADERS, 
                                                        new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                }
                final Map<String, List<String>> cxfHeaders =
                    CastUtils.cast((Map<?, ?>) cxfExchange.getOutMessage().get(org.apache.cxf.message.Message.PROTOCOL_HEADERS));

                if (!cxfHeaders.containsKey(Exchange.CONTENT_TYPE)) {
                    List<String> a = Arrays.asList((String)response.getHeader(Exchange.CONTENT_TYPE));
                    cxfHeaders.put(Exchange.CONTENT_TYPE, a);
                    cxfExchange.getOutMessage().put(Exchange.CONTENT_TYPE, response.getHeader(Exchange.CONTENT_TYPE));
                }
            }
        }
        return o;
    }

    public void populateExchangeFromCxfRsRequest(org.apache.cxf.message.Exchange cxfExchange,
                                                 Exchange camelExchange, Method method, Object[] paramArray) {
        Message camelMessage = camelExchange.getIn();        
        //Copy the CXF message header into the Camel inMessage
        org.apache.cxf.message.Message cxfMessage = cxfExchange.getInMessage();

        CxfHeaderHelper.copyHttpHeadersFromCxfToCamel(headerFilterStrategy, cxfMessage, camelMessage, camelExchange);

        // TODO move to CxfHeaderHelper and use header filter strategy and CXF_TO_CAMEL_HEADERS
        // setup the charset from content-type header
        setCharsetWithContentType(camelExchange);
        
        //copy the protocol header
        copyProtocolHeader(cxfMessage, camelMessage, camelMessage.getExchange());
        
        camelMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_CLASS, method.getReturnType());
        
        camelMessage.setHeader(CxfConstants.CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE, method.getGenericReturnType());
               
        copyOperationResourceInfoStack(cxfMessage, camelMessage);
        
        camelMessage.setHeader(CxfConstants.OPERATION_NAME, method.getName());
        
        camelMessage.setHeader(CxfConstants.CAMEL_CXF_MESSAGE, cxfMessage);
        
        camelMessage.setBody(new MessageContentsList(paramArray));
        
        // propagate the security subject from CXF security context
        SecurityContext securityContext = cxfMessage.get(SecurityContext.class);
        if (securityContext instanceof LoginSecurityContext
            && ((LoginSecurityContext)securityContext).getSubject() != null) {
            camelExchange.getIn().getHeaders().put(Exchange.AUTHENTICATION, 
                                                   ((LoginSecurityContext)securityContext).getSubject());
        } else if (securityContext != null && securityContext.getUserPrincipal() != null) {
            Subject subject = new Subject();
            subject.getPrincipals().add(securityContext.getUserPrincipal());
            camelExchange.getIn().getHeaders().put(Exchange.AUTHENTICATION, subject);
        }
    }
    
    protected void setCharsetWithContentType(Exchange camelExchange) {
        // setup the charset from content-type header
        String contentTypeHeader = ExchangeHelper.getContentType(camelExchange);
        if (contentTypeHeader != null) {
            String charset = HttpHeaderHelper.findCharset(contentTypeHeader);
            String normalizedEncoding = HttpHeaderHelper.mapCharset(charset, Charset.forName("UTF-8").name());
            if (normalizedEncoding != null) {
                camelExchange.setProperty(Exchange.CHARSET_NAME, normalizedEncoding);
            }
        }
    }

    
    public MultivaluedMap<String, String> bindCamelHeadersToRequestHeaders(Map<String, Object> camelHeaders,
                                                                           Exchange camelExchange)
        throws Exception {

        MultivaluedMap<String, String> answer = new MetadataMap<>();
        CxfHeaderHelper.propagateCamelHeadersToCxfHeaders(headerFilterStrategy, camelHeaders, answer, camelExchange);
        return answer;
    }

    /**
     * This method call Message.getBody({@link MessageContentsList}) to allow
     * an appropriate converter to kick in even through we only read the first
     * element off the MessageContextList.  If that returns null, we check  
     * the body to see if it is a List or an array and then return the first 
     * element.  If that fails, we will simply return the object.
     */
    public Object bindCamelMessageBodyToRequestBody(Message camelMessage, Exchange camelExchange)
        throws Exception {

        Object request = camelMessage.getBody(MessageContentsList.class);
        if (request != null) {
            return ((MessageContentsList)request).get(0);
        } 

        request = camelMessage.getBody();
        if (request instanceof List) {
            request = ((List<?>)request).get(0);
        } else if (request != null && request.getClass().isArray()) {
            request = ((Object[])request)[0];
        }

        return request;
    }

    /**
     * We will return an empty Map unless the response parameter is a {@link Response} object. 
     */
    public Map<String, Object> bindResponseHeadersToCamelHeaders(Object response, Exchange camelExchange)
        throws Exception {
        
        Map<String, Object> answer = new HashMap<>();
        if (response instanceof Response) {
            Map<String, List<Object>> responseHeaders = ((Response) response).getMetadata();
            CxfHeaderHelper.propagateCxfHeadersToCamelHeaders(headerFilterStrategy, responseHeaders, answer, camelExchange);
        }

        return answer;
    }

    public Entity<Object> bindCamelMessageToRequestEntity(Object body, Message camelMessage, Exchange camelExchange) throws Exception  {
        if (body == null) {
            return null;
        }
        String contentType = camelMessage.getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType == null) {
            contentType = MediaType.WILDCARD;
        }
        String contentEncoding = camelMessage.getHeader(Exchange.CONTENT_ENCODING, String.class);
        return  Entity.entity(body, new Variant(MediaType.valueOf(contentType), Locale.US, contentEncoding));
    }

    /**
     *  By default, we just return the response object. 
     */
    public Object bindResponseToCamelBody(Object response, Exchange camelExchange) throws Exception {
        return response;
    }
    
    public HeaderFilterStrategy getHeaderFilterStrategy() {        
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;        
    }
    
    @SuppressWarnings("unchecked")
    protected void copyProtocolHeader(org.apache.cxf.message.Message cxfMessage, Message camelMessage, Exchange camelExchange) {
        Map<String, List<String>> headers = (Map<String, List<String>>)cxfMessage.get(org.apache.cxf.message.Message.PROTOCOL_HEADERS);
        for (Map.Entry<String, List<String>>entry : headers.entrySet()) {
            // just make sure the first String element is not null
            if (headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), camelExchange) 
                || entry.getValue().isEmpty()) {
                LOG.trace("Drop CXF message protocol header: {}={}", entry.getKey(), entry.getValue());
            } else {
                // just put the first String element, as the complex one is filtered
                camelMessage.setHeader(entry.getKey(), entry.getValue().get(0));
            }
            continue;
        }
    }
    
    protected void copyOperationResourceInfoStack(org.apache.cxf.message.Message cxfMessage, Message camelMessage) {
        OperationResourceInfoStack stack = cxfMessage.get(OperationResourceInfoStack.class);
        if (stack != null) {
            // make a copy of the operation resource info for looking up the sub resource location
            OperationResourceInfoStack copyStack = (OperationResourceInfoStack)stack.clone();
            camelMessage.setHeader(CxfConstants.CAMEL_CXF_RS_OPERATION_RESOURCE_INFO_STACK, copyStack);
                        
        }
    }

}
