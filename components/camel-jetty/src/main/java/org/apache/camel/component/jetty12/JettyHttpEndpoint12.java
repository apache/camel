/*
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
package org.apache.camel.component.jetty12;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpConstants;
import org.apache.camel.component.jetty.JettyHttpEndpoint;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;

/**
 * Expose HTTP endpoints using Jetty 12.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "jetty", extendsScheme = "http", title = "Jetty", syntax = "jetty:httpUri",
             category = { Category.HTTP }, consumerOnly = true, lenientProperties = true,
             headersClass = JettyHttpConstants.class)
@Metadata(excludeProperties = "authMethod,authUsername,authPassword,authDomain,authHost,authBearerToken,"
                              + "proxyAuthScheme,proxyAuthMethod,proxyAuthUsername,proxyAuthPassword,proxyAuthHost,proxyAuthPort,proxyAuthDomain",
          annotations = {
                  "protocol=http"
          })
@BodyTypes(consumerOut = @BodyType(value = { Carrier.STREAM, Carrier.NULL }, dependsOn = { "transferException", "httpBinding" },
                                   description = "The request body as a stream cache, which can be read again (the raw stream,"
                                                 + " read once, with disableStreamCache); an empty one for a GET, null with"
                                                 + " Content-Length 0 or eagerCheckContentAvailable. The fields of a form are"
                                                 + " also headers; a multipart upload leaves the body empty and gives its files"
                                                 + " as attachments and DataHandler headers"),
           consumerReply = @BodyType(value = { Carrier.NULL, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                     description = "The response body: text, bytes, a stream or a file. A Map, a List or a POJO"
                                                   + " is sent as an empty body (as its toString with chunked=false): marshal"
                                                   + " it first"))
public class JettyHttpEndpoint12 extends JettyHttpEndpoint implements AsyncEndpoint {

    private HttpBinding binding;

    public JettyHttpEndpoint12(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(component, uri, httpURL);
    }

    @Override
    public HttpBinding getHttpBinding() {
        // make sure we include jetty10 variant of the http binding
        if (this.binding == null) {
            this.binding = new AttachmentHttpBinding();
            this.binding.setTransferException(isTransferException());
            this.binding.setMuteException(isMuteException());
            this.binding.setLogException(isLogException());
            if (getComponent() != null) {
                this.binding.setAllowJavaSerializedObject(getComponent().isAllowJavaSerializedObject());
                this.binding.setDeserializationFilter(getComponent().getDeserializationFilter());
            }
            this.binding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            this.binding.setEagerCheckContentAvailable(isEagerCheckContentAvailable());
            this.binding.setMapHttpMessageBody(isMapHttpMessageBody());
            this.binding.setMapHttpMessageHeaders(isMapHttpMessageHeaders());
            this.binding.setMapHttpMessageFormUrlEncodedBody(isMapHttpMessageFormUrlEncodedBody());
        }
        return this.binding;
    }

    @Override
    public void setHttpBinding(HttpBinding binding) {
        super.setHttpBinding(binding);
        this.binding = binding;
    }

}
