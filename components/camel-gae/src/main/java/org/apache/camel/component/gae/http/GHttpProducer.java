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
package org.apache.camel.component.gae.http;

import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultProducer;

public class GHttpProducer extends DefaultProducer {

    public GHttpProducer(GHttpEndpoint endpoint) {
        super(endpoint);
    }
    
    @Override
    public GHttpEndpoint getEndpoint() {
        return (GHttpEndpoint)super.getEndpoint();
    }
    
    public OutboundBinding<GHttpEndpoint, HTTPRequest, HTTPResponse> getOutboundBinding() {
        return getEndpoint().getOutboundBinding();
    }
    
    public URLFetchService getUrlFetchService() {
        return getEndpoint().getUrlFetchService();
    }

    /**
     * Invokes the URL fetch service.
     * 
     * @param exchange contains the request data in the in-message. The result is written to the out-message.
     * @throws GHttpException if the response code is >= 400 and {@link GHttpEndpoint#isThrowExceptionOnFailure()}
     * returns <code>true</code>.
     *
     * @see GHttpBinding
     */
    public void process(Exchange exchange) throws Exception {
        HTTPRequest request = getOutboundBinding().writeRequest(getEndpoint(), exchange, null);
        HTTPResponse response = getUrlFetchService().fetch(request);
        getOutboundBinding().readResponse(getEndpoint(), exchange, response);
    }

}
