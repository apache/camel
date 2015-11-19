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

import java.net.URI;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import org.apache.camel.Endpoint;
import org.apache.camel.component.gae.bind.InboundBinding;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletEndpoint;

/**
 * The <a href="http://camel.apache.org/ghttp.html">Google App Engine HTTP
 * Component</a> supports HTTP-based inbound and outbound communication. Inbound
 * HTTP communication is realized in terms of the <a
 * href="http://camel.apache.org/servlet.html"> Servlet Component</a> component.
 * Outbound HTTP communication uses the URL fetch service of the Google App
 * Engine.
 */
public class GHttpComponent extends ServletComponent {
    public GHttpComponent() {
        super(GHttpEndpoint.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean throwException = getAndRemoveParameter(parameters, "throwExceptionOnFailure", Boolean.class, true); 
        boolean bridgeEndpoint = getAndRemoveParameter(parameters, "bridgeEndpoint", Boolean.class, true); 
        OutboundBinding<GHttpEndpoint, HTTPRequest, HTTPResponse> outboundBinding = resolveAndRemoveReferenceParameter(
                parameters, "outboundBindingRef", OutboundBinding.class, new GHttpBinding());
        InboundBinding<GHttpEndpoint, HttpServletRequest, HttpServletResponse> inboundBinding = resolveAndRemoveReferenceParameter(
                parameters, "inboundBindingRef", InboundBinding.class, new GHttpBinding());
        GHttpEndpoint endpoint = (GHttpEndpoint)super.createEndpoint(uri, remaining, parameters);
        endpoint.setThrowExceptionOnFailure(throwException);
        endpoint.setBridgeEndpoint(bridgeEndpoint);
        endpoint.setOutboundBinding(outboundBinding);
        endpoint.setInboundBinding(inboundBinding);
        return endpoint;
    }

    @Override
    protected ServletEndpoint createServletEndpoint(String endpointUri, ServletComponent component, URI httpUri) throws Exception {
        return new GHttpEndpoint(endpointUri, component, httpUri);
    }

    @Override
    protected boolean lenientContextPath() {
        // must use the path as-is
        return false;
    }
}
