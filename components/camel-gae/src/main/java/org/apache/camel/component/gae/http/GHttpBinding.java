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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.gae.bind.InboundBinding;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import static org.apache.camel.component.gae.http.GHttpEndpoint.getEndpointUrl;
import static org.apache.camel.util.GZIPHelper.isGzip;

/**
 * Binds the {@link HTTPRequest}/{@link HTTPResponse} pair of the URL fetch
 * service to a Camel {@link Exchange}.
 */
public class GHttpBinding implements 
    OutboundBinding <GHttpEndpoint, HTTPRequest, HTTPResponse>, 
    InboundBinding  <GHttpEndpoint, HttpServletRequest, HttpServletResponse> { 
    
    // ----------------------------------------------------------------
    //  Outbound binding
    // ----------------------------------------------------------------
    
    /**
     * Reads data from <code>response</code> and writes it to the out-message of
     * the <code>exchange</code>.
     * 
     * @return the original <code>exchange</code> instance populated with response data.
     * @throws GHttpException if the response code is >= 400 and
     *             {@link GHttpEndpoint#isThrowExceptionOnFailure()} returns
     *             <code>true</code>.
     */
    public Exchange readResponse(GHttpEndpoint endpoint, Exchange exchange, HTTPResponse response) throws Exception {
        int responseCode = response.getResponseCode();
        readResponseHeaders(endpoint, exchange, response);
        readResponseBody(endpoint, exchange, response);
        if (responseCode >= 400 && endpoint.isThrowExceptionOnFailure()) {
            throw new GHttpException(responseCode, 
                exchange.getOut().getBody(InputStream.class), 
                exchange.getOut().getHeaders());
        }
        return exchange;
    }

    /**
     * Reads data from <code>exchange</code> and writes it to a newly created
     * {@link HTTPRequest} instance. The <code>request</code> parameter is
     * ignored.
     *
     * @return a newly created {@link HTTPRequest} instance containing data from
     *         <code>exchange</code>.
     */
    public HTTPRequest writeRequest(GHttpEndpoint endpoint, Exchange exchange, HTTPRequest request) throws Exception {
        HTTPRequest answer = new HTTPRequest(
                getRequestUrl(endpoint, exchange), 
                getRequestMethod(endpoint, exchange));
        writeRequestHeaders(endpoint, exchange, answer);
        writeRequestBody(endpoint, exchange, answer);
        return answer;
    }
    
    // ----------------------------------------------------------------
    //  Inbound binding
    // ----------------------------------------------------------------
    
    public Exchange readRequest(GHttpEndpoint endpoint, Exchange exchange, HttpServletRequest request) {
        readRequestHeaders(endpoint, exchange, request);
        return exchange;
    }

    public HttpServletResponse writeResponse(GHttpEndpoint endpoint, Exchange exchange, HttpServletResponse response) {
        return response;
    }

    // ----------------------------------------------------------------
    //  Customization points
    // ----------------------------------------------------------------
    
    protected void readResponseHeaders(GHttpEndpoint endpoint, Exchange exchange, HTTPResponse response) {
        HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();
        
        Message in = exchange.getIn();
        Message out = exchange.getOut();
        
        out.setHeaders(in.getHeaders());
        out.setHeader(Exchange.HTTP_RESPONSE_CODE, response.getResponseCode());
        
        String contentType = getResponseHeader("Content-Type", response);
        if (contentType != null) {
            out.setHeader(Exchange.CONTENT_TYPE, contentType);
        }
        
        for (HTTPHeader header : response.getHeaders()) {
            String name = header.getName();
            String value = header.getValue();
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                out.setHeader(name, value);
            }
        }
    }
    
    protected void readRequestHeaders(GHttpEndpoint endpoint, Exchange exchange, HttpServletRequest request) {
        // EXPERIMENTAL // TODO: resolve gzip encoding issues
        exchange.getIn().removeHeader("Accept-Encoding");
        exchange.getIn().removeHeader("Content-Encoding");
    }

    protected void writeRequestHeaders(GHttpEndpoint endpoint, Exchange exchange, HTTPRequest request) {
        HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();
        for (String headerName : exchange.getIn().getHeaders().keySet()) {
            String headerValue = exchange.getIn().getHeader(headerName, String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {
                request.addHeader(new HTTPHeader(headerName, headerValue));
            }
        }
    }
    
    protected void readResponseBody(GHttpEndpoint endpoint, Exchange exchange, HTTPResponse response) throws Exception {
        byte[] content = response.getContent();
        if (content != null) {
            InputStream stream = new ByteArrayInputStream(content);
            if (isGzip(getResponseHeader("Content-Encoding", response))) {
                stream = new GZIPInputStream(stream);
            }
            exchange.getOut().setBody(stream);
        }
    }
    
    protected void writeRequestBody(GHttpEndpoint endpoint, Exchange exchange, HTTPRequest request) {
        request.setPayload(exchange.getIn().getBody(byte[].class));
    }
    
    protected URL getRequestUrl(GHttpEndpoint endpoint, Exchange exchange) throws Exception {
        String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
        String query = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (uri != null && !endpoint.isBridgeEndpoint()) {
            return getEndpointUrl(UnsafeUriCharactersEncoder.encodeHttpURI(uri), query);
        }
        return getEndpointUrl(endpoint.getEndpointUri(), query);
    }
    
    protected HTTPMethod getRequestMethod(GHttpEndpoint endpoint, Exchange exchange) {
        String method = (String)exchange.getIn().getHeader(Exchange.HTTP_METHOD);
        if (method != null) {
            return HTTPMethod.valueOf(method);
        } else if (exchange.getIn().getBody() != null) {
            return HTTPMethod.POST;
        } else {
            return HTTPMethod.GET;
        }
    }
    
    protected String getResponseHeader(String name, HTTPResponse response) {
        for (HTTPHeader header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase(name)) {
                return header.getValue();
            }
        }
        return null;
    }

}
