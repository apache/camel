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
package org.apache.camel.component.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.eclipse.jetty.client.HttpClient;

public interface JettyContentExchange {

    void init(Exchange exchange, JettyHttpBinding jettyBinding, HttpClient client, AsyncCallback callback);

    // Methods to prepare the request
    void setRequestContentType(String contentType);

    void setMethod(String method);

    void setTimeout(long timeout);

    void setURL(String url);

    void setRequestContent(byte[] byteArray);

    void setRequestContent(String data, String charset) throws UnsupportedEncodingException;

    void setRequestContent(InputStream ins);
    
    void setRequestContent(InputStream ins, int contentLength);

    void addRequestHeader(String key, String s);

    void setSupportRedirect(boolean supportRedirect);

    /*
     * Send using jetty HttpClient and return. The callback will be called when the response arrives
     */
    void send(HttpClient client) throws IOException;

    // Methods to retrieve the response
    
    byte[] getBody();

    String getUrl();

    int getResponseStatus();

    byte[] getResponseContentBytes();

    Map<String, Collection<String>> getResponseHeaders();

    Map<String, Collection<String>> getRequestHeaders();
}
