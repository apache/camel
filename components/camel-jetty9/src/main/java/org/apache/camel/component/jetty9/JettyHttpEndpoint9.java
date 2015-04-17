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
package org.apache.camel.component.jetty9;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.jetty.JettyContentExchange;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;
import org.apache.camel.spi.UriEndpoint;

@UriEndpoint(scheme = "jetty", title = "Jetty 9", syntax = "jetty:httpUri", consumerClass = HttpConsumer.class, label = "http")
public class JettyHttpEndpoint9 extends JettyHttpEndpoint {
    private HttpBinding binding;

    public JettyHttpEndpoint9(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(component, uri, httpURL);
    }
    
    @Override
    public HttpBinding getBinding() {
        if (this.binding == null) {
            this.binding = new AttachmentHttpBinding(this);
        }
        return this.binding;
    }

    @Override
    public void setBinding(HttpBinding binding) {
        super.setBinding(binding);
        this.binding = binding;
    }
    
    @Override
    public JettyContentExchange createContentExchange() {
        return new JettyContentExchange9();
    } 
}
