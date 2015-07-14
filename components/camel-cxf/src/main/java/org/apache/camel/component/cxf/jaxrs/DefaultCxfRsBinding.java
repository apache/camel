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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.util.CxfUtils;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ExchangeHelper;
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

    protected Map<String, String> camelToCxfHeaderMap = new HashMap<String, String>();
    protected Map<String, String> cxfToCamelHeaderMap = new HashMap<String, String>();
    
    private HeaderFilterStrategy headerFilterStrategy;
    
    public DefaultCxfRsBinding() {
        // initialize mappings between Camel and CXF header names
        
        camelToCxfHeaderMap.put(Exchange.HTTP_URI, org.apache.cxf.message.Message.REQUEST_URI);
        camelToCxfHeaderMap.put(Exchange.HTTP_METHOD, org.apache.cxf.message.Message.HTTP_REQUEST_METHOD);
        camelToCxfHeaderMap.put(Exchange.HTTP_PATH, org.apache.cxf.message.Message.PATH_INFO);
        camelToCxfHeaderMap.put(Exchange.CONTENT_TYPE, org.apache.cxf.message.Message.CONTENT_TYPE);
        camelToCxfHeaderMap.put(Exchange.HTTP_CHARACTER_ENCODING, org.apache.cxf.message.Message.ENCODING);
        camelToCxfHeaderMap.put(Exchange.HTTP_QUERY, org.apache.cxf.message.Message.QUERY_STRING);
        camelToCxfHeaderMap.put(Exchange.ACCEPT_CONTENT_TYPE, org.apache.cxf.message.Message.ACCEPT_CONTENT_TYPE);
    
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.REQUEST_URI, Exchange.HTTP_URI);
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.HTTP_REQUEST_METHOD, Exchange.HTTP_METHOD);
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.PATH_INFO, Exchange.HTTP_PATH);
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.CONTENT_TYPE, Exchange.CONTENT_TYPE);
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.ENCODING, Exchange.HTTP_CHARACTER_ENCODING);
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.QUERY_STRING, Exchange.HTTP_QUERY);
        cxfToCamelHeaderMap.put(org.apache.cxf.message.Message.ACCEPT_CONTENT_TYPE, Exchange.ACCEPT_CONTENT_TYPE);
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

        return response.getBody();
    }

    public void populateExchangeFromCxfRsRequest(org.apache.cxf.message.Exchange cxfExchange,
                                                 Exchange camelExchange, Method method, Object[] paramArray) {
        Message camelMessage = camelExchange.getIn();        
        //Copy the CXF message header into the Camel inMessage
        org.apache.cxf.message.Message cxfMessage = cxfExchange.getInMessage();
        
        // TODO use header filter strategy and cxfToCamelHeaderMap
        CxfUtils.copyHttpHeadersFromCxfToCamel(cxfMessage, camelMessage);
        
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

        MultivaluedMap<String, String> answer = new MetadataMap<String, String>();
        for (Map.Entry<String, Object> entry : camelHeaders.entrySet()) {
            // Need to make sure the cxf needed header will not be filtered 
            if (headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), camelExchange)
                && camelToCxfHeaderMap.get(entry.getKey()) == null) {
                LOG.trace("Drop Camel header: {}={}", entry.getKey(), entry.getValue());
                continue;
            }
            
            // we need to make sure the entry value is not null
            if (entry.getValue() == null) {
                LOG.trace("Drop Camel header: {}={}", entry.getKey(), entry.getValue());
                continue;
            }
            
            String mappedHeaderName = camelToCxfHeaderMap.get(entry.getKey());
            if (mappedHeaderName == null) {
                mappedHeaderName = entry.getKey();
            }
            
            LOG.trace("Propagate Camel header: {}={} as {}",
                new Object[]{entry.getKey(), entry.getValue(), mappedHeaderName});
            
            answer.putSingle(mappedHeaderName, entry.getValue().toString());
        }
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
        
        Map<String, Object> answer = new HashMap<String, Object>();
        if (response instanceof Response) {
            
            for (Map.Entry<String, List<Object>> entry : ((Response)response).getMetadata().entrySet()) {
                if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), 
                                                                       entry.getValue(), camelExchange)) {
                    
                    String mappedHeaderName = cxfToCamelHeaderMap.get(entry.getKey());
                    if (mappedHeaderName == null) {
                        mappedHeaderName = entry.getKey();
                    }
                    
                    LOG.trace("Populate external header {}={} as {}",
                        new Object[]{entry.getKey(), entry.getValue(), mappedHeaderName});
                    
                    answer.put(mappedHeaderName, entry.getValue().get(0));

                } else {
                    LOG.trace("Drop external header {}={}", entry.getKey(), entry.getValue());
                }
            }
            
        }
        
        return answer;
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
    
    public Map<String, String> getCamelToCxfHeaderMap() {
        return camelToCxfHeaderMap;
    }

    public void setCamelToCxfHeaderMap(Map<String, String> camelToCxfHeaderMap) {
        this.camelToCxfHeaderMap = camelToCxfHeaderMap;
    }

    public Map<String, String> getCxfToCamelHeaderMap() {
        return cxfToCamelHeaderMap;
    }

    public void setCxfToCamelHeaderMap(Map<String, String> cxfToCamelHeaderMap) {
        this.cxfToCamelHeaderMap = cxfToCamelHeaderMap;
    }
    
    protected void copyMessageHeader(org.apache.cxf.message.Message cxfMessage, Message camelMessage, String cxfKey, String camelKey) {
        if (cxfMessage.get(cxfKey) != null) {
            camelMessage.setHeader(camelKey, cxfMessage.get(cxfKey));
        }
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
