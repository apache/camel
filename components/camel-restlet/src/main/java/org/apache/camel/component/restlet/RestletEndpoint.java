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
package org.apache.camel.component.restlet;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.restlet.data.Method;

/**
 * Represents a <a href="http://www.restlet.org/"> endpoint</a>
 *
 * @version $Revision$
 */
public class RestletEndpoint extends DefaultEndpoint {
    
    private static final int DEFAULT_PORT = 80;
    private static final String DEFAULT_PROTOCOL = "http";
    private static final String DEFAULT_HOST = "localhost";
    
    private Method restletMethod = Method.GET;
    private String protocol = DEFAULT_PROTOCOL;
    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String uriPattern;
    private RestletBinding restletBinding;
    private Map<String, String> realm;

    public RestletEndpoint(RestletComponent component, String remaining, RestletBinding restletBinding) throws Exception {
        super(remaining, component);
        this.restletBinding = restletBinding;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override 
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system.
        return true;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new RestletConsumer(this, processor);
    }

    public Producer createProducer() throws Exception {
        return new RestletProducer(this);
    }

    public void connect(RestletConsumer restletConsumer) throws Exception {
        ((RestletComponent)getComponent()).connect(restletConsumer);
    }

    public void disconnect(RestletConsumer restletConsumer) throws Exception {
        ((RestletComponent)getComponent()).disconnect(restletConsumer);        
    }

    public Method getRestletMethod() {
        return restletMethod;
    }

    public void setRestletMethod(Method restletMethod) {
        this.restletMethod = restletMethod;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUriPattern() {
        return uriPattern;
    }

    public void setUriPattern(String uriPattern) {
        this.uriPattern = uriPattern;
    }

    public RestletBinding getRestletBinding() {
        return restletBinding;
    }

    public void setRestletBinding(RestletBinding restletBinding) {
        this.restletBinding = restletBinding;
    }

    public Map<String, String> getRealm() {
        return realm;
    }

    public void setRealm(Map<String, String> realm) {
        this.realm = realm;
    }
}
