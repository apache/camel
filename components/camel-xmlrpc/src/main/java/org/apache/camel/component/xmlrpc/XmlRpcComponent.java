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
package org.apache.camel.component.xmlrpc;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * Represents the component that manages {@link XmlRpcEndpoint}.
 */
public class XmlRpcComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // current we just use the uri as the server address
        XmlRpcEndpoint endpoint = new XmlRpcEndpoint(uri, this, remaining);
        XmlRpcClientConfigImpl clientConfig = endpoint.getClientConfig();
        // find out the clientConfigurer first
        XmlRpcClientConfigurer clientConfigurer = resolveAndRemoveReferenceParameter(parameters, "clientConfigurer", XmlRpcClientConfigurer.class);
        endpoint.setClientConfigurer(clientConfigurer);
        // we just use the XmlRpcClientConfig to take the parameters
        setProperties(clientConfig, parameters);
        return endpoint;
    }
}
