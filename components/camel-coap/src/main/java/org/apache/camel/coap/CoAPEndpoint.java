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
package org.apache.camel.coap;

import java.net.URI;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.eclipse.californium.core.CoapServer;

/**
 * The coap component is used for sending and receiving messages from COAP capable devices.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "coap", title = "CoAP", syntax = "coap:uri", consumerClass = CoAPConsumer.class, label = "iot")
public class CoAPEndpoint extends DefaultEndpoint {
    @UriPath
    private URI uri;
    @UriParam(label = "consumer")
    private String coapMethodRestrict;
        
    private CoAPComponent component;
    
    public CoAPEndpoint(String uri, CoAPComponent component) {
        super(uri, component);
        try {
            this.uri = new URI(uri);
        } catch (java.net.URISyntaxException use) {
            this.uri = null;
        }
        this.component = component;
    }

    public void setCoapMethodRestrict(String coapMethodRestrict) {
        this.coapMethodRestrict = coapMethodRestrict;
    }

    /**
     * Comma separated list of methods that the CoAP consumer will bind to. The default is to bind to all methods (DELETE, GET, POST, PUT).
     */
    public String getCoapMethodRestrict() {
        return this.coapMethodRestrict;
    }

    public Producer createProducer() throws Exception {
        return new CoAPProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CoAPConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
    
    public void setUri(URI u) {
        uri = u;
    }
    
    /**
     * The URI for the CoAP endpoint
     */
    public URI getUri() {
        return uri;
    }

    public CoapServer getCoapServer() {
        return component.getServer(getUri().getPort());
    }
}
