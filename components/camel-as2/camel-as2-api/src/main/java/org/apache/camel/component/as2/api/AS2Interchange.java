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
package org.apache.camel.component.as2.api;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

/**
 * AS2 Interchange
 * 
 * <p>Represents the interchange of AS2 messages  
 */
public class AS2Interchange {
    
    private HttpRequest request;
    private HttpResponse response;
    private HttpContext context;
    
    /**
     * The HTTP request of the AS2 interchange.
     * 
     * @return The HTTP request
     */
    public HttpRequest getRequest() {
        return request;
    }
    
    /**
     * The HTTP request of the AS2 interchange.
     * 
     * @param request - the HTTP request
     */
    public void setRequest(HttpRequest request) {
        this.request = request;
    }
    
    /**
     * The HTTP response of the AS2 interchange.
     * 
     * @return The HTTP response
     */
    public HttpResponse getResponse() {
        return response;
    }
    
    /**
     * The HTTP response of the AS2 interchange.
     * 
     * @param response = the HTTP response
     */
    public void setResponse(HttpResponse response) {
        this.response = response;
    }
    
    /**
     * The HTTP context of the AS2 interchange.
     * 
     * @return The HTTP context.
     */
    public HttpContext getContext() {
        return context;
    }
    
    /**
     * The HTTP context of the AS2 interchange.
     * 
     * @param context - the HTTP context.
     */
    public void setContext(HttpContext context) {
        this.context = context;
    }
    
}
