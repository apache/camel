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
package org.apache.camel.component.cxf.common.message;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @version 
 */
public class DefaultCxfMessageMapper implements CxfMessageMapper {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCxfMessageMapper.class);
    private static final String CXF_HTTP_REQUEST = "HTTP.REQUEST";
    private static final String CXF_HTTP_RESPONSE = "HTTP.RESPONSE";
    
    public Message createCxfMessageFromCamelExchange(Exchange camelExchange, 
            HeaderFilterStrategy headerFilterStrategy) {
        
        org.apache.cxf.message.Message answer = 
            CxfMessageHelper.getCxfInMessage(headerFilterStrategy, camelExchange, false);
        
        org.apache.camel.Message camelMessage = camelExchange.getIn();
        String requestContentType = getRequestContentType(camelMessage); 
        
        String acceptContentTypes = camelMessage.getHeader("Accept", String.class);
        if (acceptContentTypes == null) {
            acceptContentTypes = "*/*";
        }
        
        String enc = getCharacterEncoding(camelMessage);
        String requestURI = getRequestURI(camelMessage);
        String path = getPath(camelMessage);
        String basePath = getBasePath(camelExchange);
        String verb = getVerb(camelMessage);
        String queryString = getQueryString(camelMessage);
        
        answer.put(org.apache.cxf.message.Message.REQUEST_URI, requestURI);
        answer.put(org.apache.cxf.message.Message.BASE_PATH, basePath);
        answer.put(org.apache.cxf.message.Message.HTTP_REQUEST_METHOD, verb);
        answer.put(org.apache.cxf.message.Message.PATH_INFO, path);
        answer.put(org.apache.cxf.message.Message.CONTENT_TYPE, requestContentType);
        answer.put(org.apache.cxf.message.Message.ACCEPT_CONTENT_TYPE, acceptContentTypes);
        answer.put(org.apache.cxf.message.Message.ENCODING, enc);
        answer.put(org.apache.cxf.message.Message.QUERY_STRING, queryString);
        
        HttpServletRequest request = (HttpServletRequest) camelMessage.getHeader(Exchange.HTTP_SERVLET_REQUEST);
        answer.put(CXF_HTTP_REQUEST, request);
        
        if (request != null) {
            setSecurityContext(answer, request);
        }
        
        Object response = camelMessage.getHeader(Exchange.HTTP_SERVLET_RESPONSE);
        answer.put(CXF_HTTP_RESPONSE, response);
        
        LOG.trace("Processing {}, requestContentType = {}, acceptContentTypes = {}, encoding = {}, path = {}, basePath = {}, verb = {}",
            new Object[]{camelExchange, requestContentType, acceptContentTypes, enc, path, basePath, verb});

        return answer;
    }
    
    protected void setSecurityContext(Message cxfMessage, final HttpServletRequest request) {
        cxfMessage.put(SecurityContext.class, new SecurityContext() {

            public Principal getUserPrincipal() {
                return request.getUserPrincipal();
            }

            @Override
            public boolean isUserInRole(String role) {
                return request.isUserInRole(role);
            }

        });
    }

    public void propagateResponseHeadersToCamel(Message cxfMessage, Exchange exchange,
                                                HeaderFilterStrategy strategy) {

        LOG.trace("Propagating response headers from CXF message {}", cxfMessage);
        
        if (strategy == null) {
            return;
        }

        Map<String, Object> camelHeaders = exchange.getOut().getHeaders();
        // copy the in message header to out message
        camelHeaders.putAll(exchange.getIn().getHeaders());
        
        Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map<?, ?>)cxfMessage.get(Message.PROTOCOL_HEADERS));
                      
        if (cxfHeaders != null) {
            for (Map.Entry<String, List<String>> entry : cxfHeaders.entrySet()) {
                if (!strategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                    camelHeaders.put(entry.getKey(), entry.getValue().get(0));
                    
                    LOG.trace("Populate header from CXF header={} value={}", entry.getKey(), entry.getValue());
                }
            }
        }

        // propagate HTTP RESPONSE_CODE
        String key = Message.RESPONSE_CODE;
        Object value = cxfMessage.get(key);
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value, exchange)) {
            camelHeaders.put(Exchange.HTTP_RESPONSE_CODE, value);
            LOG.trace("Populate header from CXF header={} value={} as {}",
                    new Object[]{key, value, Exchange.HTTP_RESPONSE_CODE}); 
        }
        
        // propagate HTTP CONTENT_TYPE
        if (cxfMessage.get(Message.CONTENT_TYPE) != null) {
            camelHeaders.put(Exchange.CONTENT_TYPE, cxfMessage.get(Message.CONTENT_TYPE));
        }
    }

    protected String getPath(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(Exchange.HTTP_PATH, String.class);
        return answer;
    }
    
    protected String getRequestURI(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(Exchange.HTTP_URI, String.class);
        return answer;
    }
    
    protected String getBasePath(Exchange camelExchange) {
        String answer = camelExchange.getIn().getHeader(Exchange.HTTP_BASE_URI, String.class);

        if (answer == null) {
            answer = camelExchange.getFromEndpoint().getEndpointUri();
            // remove leading scheme before the http(s) transport so we build a correct base path
            answer = answer.replaceFirst("^\\w+:http", "http");
            answer = answer.replaceFirst("^\\w+:https", "https");
        }
        
        return answer;
    }

    protected String getVerb(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(Exchange.HTTP_METHOD, String.class);
        return answer;
    }
    
    protected String getQueryString(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(Exchange.HTTP_QUERY, String.class);
        return answer;
    }

    protected String getCharacterEncoding(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(Exchange.HTTP_CHARACTER_ENCODING, String.class);
        if (answer == null) {
            answer = camelMessage.getHeader(Exchange.CHARSET_NAME, String.class);
        }
        return answer;
    }

    protected String getRequestContentType(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(Exchange.CONTENT_TYPE, String.class);
        if (answer != null) {
            return answer;
        }        
        // return default
        return "*/*";
    }

}
