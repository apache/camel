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

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.gae.bind.HttpBindingInvocationHandler;
import org.apache.camel.component.gae.bind.InboundBinding;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.component.gae.bind.OutboundBindingSupport;
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * The ghttp component provides HTTP connectivity to the GAE.
 */
@UriEndpoint(scheme = "ghttp", extendsScheme = "servlet", title = "Google HTTP",
        syntax = "ghttp:httpUri", producerOnly = true, label = "cloud,paas", lenientProperties = true)
public class GHttpEndpoint extends ServletEndpoint implements OutboundBindingSupport<GHttpEndpoint, HTTPRequest, HTTPResponse> {

    public static final String GHTTP_SCHEME = "ghttp";
    public static final String GHTTPS_SCHEME = "ghttps";
    public static final String HTTP_SCHEME = "http";
    public static final String HTTPS_SCHEME = "https";
    
    private URLFetchService urlFetchService;
    
    private OutboundBinding<GHttpEndpoint, HTTPRequest, HTTPResponse> outboundBinding;
    private InboundBinding<GHttpEndpoint, HttpServletRequest, HttpServletResponse> inboundBinding;
    
    public GHttpEndpoint(String endpointUri, ServletComponent component, URI httpUri) throws URISyntaxException {
        // set the endpoint uri with httpUri as we need to create http producer here
        super(httpUri.toString(), component, httpUri);
        urlFetchService = URLFetchServiceFactory.getURLFetchService();
    }

    /**
     * Constructs a {@link URL} from an <code>uri</code> and an optional
     * <code>query</code> string. The encoding strategy follow those of the
     * Camel HTTP component.
     * 
     * @param uri
     *            must be encoded with
     *            {@link UnsafeUriCharactersEncoder#encode(String)}.
     * @param query
     *            decoded query string. Replaces the query part of
     *            <code>uri</code> if not <code>null</code>.
     */
    static URL getEndpointUrl(String uri, String query) throws Exception {
        Map<String, Object> parameters = null;
        URI uriObj = new URI(uri);
        if (query == null) {
            parameters = URISupport.parseParameters(uriObj);
        } else {
            parameters = URISupport.parseQuery(query, false, true);
        }
        if (uriObj.getScheme().equals(GHTTPS_SCHEME)) {
            uriObj = new URI(HTTPS_SCHEME + ":" + uriObj.getRawSchemeSpecificPart());
        } else { // ghttp or anything else
            uriObj = new URI(HTTP_SCHEME + ":" + uriObj.getRawSchemeSpecificPart());
        }
        return URISupport.createRemainingURI(uriObj, parameters).toURL();
    }
    
    public URL getEndpointUrl() throws Exception {
        return getEndpointUrl(getEndpointUri(), null);
    }
    
    public URLFetchService getUrlFetchService() {
        return urlFetchService;
    }

    public void setUrlFetchService(URLFetchService urlFetchService) {
        this.urlFetchService = urlFetchService;
    }

    public OutboundBinding<GHttpEndpoint, HTTPRequest, HTTPResponse> getOutboundBinding() {
        return outboundBinding;
    }
    
    public void setOutboundBinding(OutboundBinding<GHttpEndpoint, HTTPRequest, HTTPResponse> outboundBinding) {
        this.outboundBinding = outboundBinding;
    }
    
    public InboundBinding<GHttpEndpoint, HttpServletRequest, HttpServletResponse> getInboundBinding() {
        return inboundBinding;
    }

    public void setInboundBinding(
            InboundBinding<GHttpEndpoint, HttpServletRequest, HttpServletResponse> inboundBinding) {
        this.inboundBinding = inboundBinding;
    }

    /**
     * Proxies the {@link org.apache.camel.http.common.HttpBinding} returned by {@link super#getBinding()}
     * with a dynamic proxy. The proxy's invocation handler further delegates to
     * {@link InboundBinding#readRequest(org.apache.camel.Endpoint, Exchange, Object)}
     * .
     * 
     * @return proxied {@link org.apache.camel.http.common.HttpBinding}.
     */
    @Override
    public HttpBinding getBinding() {
        return (HttpBinding)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {HttpBinding.class}, 
                new HttpBindingInvocationHandler<GHttpEndpoint, HttpServletRequest, HttpServletResponse>(
                        this, super.getBinding(), getInboundBinding()));
    }

    public Producer createProducer() throws Exception {
        return new GHttpProducer(this);
    }

    @Override
    public boolean isLenientProperties() {
        // GHttpEndpoint could not know about all it's options on the passed URI
        return true;
    }

}
