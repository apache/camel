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
package org.apache.camel.component.cxf.cxfbean;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.CxfSoapBinding;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.message.Message;

/**
 *
 * @version $Revision$
 */
public class DefaultCxfBeanBinding implements CxfBeanBinding {
    private static final Log LOG = LogFactory.getLog(DefaultCxfBeanBinding.class);

    public Message createCxfMessageFromCamelExchange(Exchange camelExchange, 
            HeaderFilterStrategy headerFilterStrategy) {
        
        org.apache.camel.Message camelMessage = camelExchange.getIn();

        // request content types
        String requestContentType = getRequestContentType(camelMessage); 
        
        // accept content types
        String acceptContentTypes = camelMessage.getHeader("Accept", String.class);
        if (acceptContentTypes == null) {
            acceptContentTypes = "*/*";
        }
        
        String enc = getCharacterEncoding(camelMessage); 
        
        // path
        String path = getPath(camelMessage);

        // base path
        String basePath = getBasePath(camelExchange);
        
        // verb
        String verb = getVerb(camelMessage);
        
        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing " + camelExchange + ", requestContentType = " + requestContentType 
                    + ", acceptContentTypes = " + acceptContentTypes + ", encoding = " + enc
                    + ", path = " + path + ", basePath = " + basePath + ", verb = " + verb); 
        }
        
    
        org.apache.cxf.message.Message answer = 
            CxfSoapBinding.getCxfInMessage(headerFilterStrategy, camelExchange, false);
        
        answer.put(org.apache.cxf.message.Message.REQUEST_URI, path);
        answer.put(org.apache.cxf.message.Message.BASE_PATH, basePath);
        answer.put(org.apache.cxf.message.Message.HTTP_REQUEST_METHOD, verb);
        answer.put(org.apache.cxf.message.Message.PATH_INFO, path);
        answer.put(org.apache.cxf.message.Message.CONTENT_TYPE, requestContentType);
        answer.put(org.apache.cxf.message.Message.ACCEPT_CONTENT_TYPE, acceptContentTypes);
        answer.put(org.apache.cxf.message.Message.ENCODING, enc);
        
        // TODO propagate security context

        return answer;
    }

    protected String getPath(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(CxfBeanHeaderNames.PATH, String.class);

        if (answer == null) {
            // try http component header
            answer = camelMessage.getHeader("CamelHttpPath", String.class);
        }
        
        return answer;
    }
    
    protected String getBasePath(Exchange camelExchange) {
        String answer = camelExchange.getIn().getHeader(CxfBeanHeaderNames.BASE_PATH, String.class);

        if (answer == null) {
            answer = camelExchange.getFromEndpoint().getEndpointUri();
        }
        
        return answer;
    }

    protected String getVerb(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(CxfBeanHeaderNames.VERB, String.class);

        if (answer == null) {
            // try http component header
            answer = camelMessage.getHeader("CamelHttpMethod", String.class);
        }
        
        return answer;
    }

    protected String getCharacterEncoding(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(CxfBeanHeaderNames.CHARACTER_ENCODING, String.class);
       
        if (answer == null) {
            // try http component header
            answer = camelMessage.getHeader("CamelHttpCharacterEncoding", String.class);
        }
        
        return answer;

    }

    protected String getRequestContentType(org.apache.camel.Message camelMessage) {
        String answer = camelMessage.getHeader(CxfBeanHeaderNames.CONTENT_TYPE, String.class);
        if (answer != null) {
            return answer;
        }
        
        // try http component header
        answer = camelMessage.getHeader("CamelHttpContentType", String.class);

        if (answer != null) {
            return answer;
        }
        
        // return default
        return "*/*";
    }

}
