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

import java.net.URI;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
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
    
    public RestletEndpoint(RestletComponent component, String remaining, 
            Map<String, String> parameters, RestletBinding restletBinding) throws Exception {
        super(remaining, component);
        this.restletBinding = restletBinding;
        
        URI u = new URI(UnsafeUriCharactersEncoder.encode(remaining));
        protocol = u.getScheme();
        
        uriPattern = u.getPath();
        if (parameters.size() > 0) {
            uriPattern = uriPattern + "?" + URISupport.createQueryString(parameters);
        }
        
        host = u.getHost();
        if (u.getPort() > 0) {
            port = u.getPort();
        }
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

    /**
     * @param restletConsumer
     */
    public void connect(RestletConsumer restletConsumer) throws Exception {
        ((RestletComponent)getComponent()).connect(restletConsumer);
    }

    /**
     * @param restletConsumer
     */
    public void disconnect(RestletConsumer restletConsumer) throws Exception {
        ((RestletComponent)getComponent()).disconnect(restletConsumer);        
    }

    /**
     * @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }
    
    /**
     * @return the uriPattern
     */
    public String getUriPattern() {
        return uriPattern;
    }

    /**
     * @return the restletBinding
     */
    public RestletBinding getRestletBinding() {
        return restletBinding;
    }

    /**
     * @param restletMethod the restletMethod to set
     */
    public void setRestletMethod(Method restletMethod) {
        this.restletMethod = restletMethod;
    }

    /**
     * @return the restletMethod
     */
    public Method getRestletMethod() {
        return restletMethod;
    }

    /**
     * @param realm
     */
    public void setRealm(Map<String, String> realm) {
        this.realm = realm;
    }
    
    /**
     * @return the realm
     */
    public Map<String, String> getRealm() {
        return realm;
    }


}
