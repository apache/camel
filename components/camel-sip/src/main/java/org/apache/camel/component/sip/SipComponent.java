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
package org.apache.camel.component.sip;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  SipComponent is a factory for the SipEndpoint. This class gets created at the start of a route building.
 *  When you're building this route for example:
 *      from(sip://johndoe@asipserver.com:5252)
 *       .to(x)
 *
 *  Camel will create a SipComponent instance and call the createEndpoint() method with the sip uri
 *  "johndoe@asipserver.com:5252"
 *
 */
public class SipComponent extends UriEndpointComponent {

    /**
     * The logger for this class. It will log the parameters given when creating an sip endpoint
     */
    private static final Logger LOG = LoggerFactory.getLogger(SipComponent.class);

    /**
     * Default constructor for an UriEndPointComponent. The UriEndPointComponent extends the defaultComponent.
     *
     */
    public SipComponent() {
        super(SipEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String parametersString = "";
        for(String s : parameters.keySet())
        {
            parametersString += String.format("%s=%s;",s, parameters.get(s).toString());
        }
        LOG.debug("Creating endpoint with uri: {} remaining: {} parameters: {}", uri, remaining, parametersString);
        SipConfiguration config = new SipConfiguration();
        config.initialize(new URI(uri), parameters, this);
        
        SipEndpoint sipEndpoint = new SipEndpoint(uri, this, config);
        setProperties(sipEndpoint.getConfiguration(), parameters);
        return sipEndpoint;
    }

}