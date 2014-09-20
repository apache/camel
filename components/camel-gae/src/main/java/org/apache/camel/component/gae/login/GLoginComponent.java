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
package org.apache.camel.component.gae.login;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.gae.auth.GAuthComponent;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * The <a href="http://camel.apache.org/glogin.html">GLogin Component</a>
 * encapsulates the required steps needed to login to an Google App Engine (GAE)
 * application. This component uses <a
 * href="http://code.google.com/apis/accounts/docs/AuthForInstalledApps.html"
 * >ClientLogin</a> for authentication and a GAE-specific mechanism for
 * authorization. Result of the login procedure is an authorization cookie that
 * should be included in HTTP requests targeted at GAE applications. This
 * component is intended for being used by Java client applications that want to
 * do a programmatic login to GAE applications. Web applications should use the
 * {@link GAuthComponent} for access authorization to other web applications.
 */
public class GLoginComponent extends UriEndpointComponent {

    public GLoginComponent() {
        super(GLoginEndpoint.class);
    }

    public GLoginComponent(CamelContext context) {
        super(context, GLoginEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        OutboundBinding<GLoginEndpoint, GLoginData, GLoginData> outboundBinding = resolveAndRemoveReferenceParameter(
                parameters, "outboundBindingRef", GLoginBinding.class, new GLoginBinding());
        GLoginService service = resolveAndRemoveReferenceParameter(
                parameters, "serviceRef", GLoginService.class, new GLoginServiceImpl());
        GLoginEndpoint endpoint = new GLoginEndpoint(uri, this, 
                getHostName(remaining),
                getDevPort(remaining));
        endpoint.setOutboundBinding(outboundBinding);
        endpoint.setService(service);
        return endpoint;
    }

    private static String getHostName(String remaining) {
        if (remaining.contains(":")) {
            return remaining.split(":")[0];
        } else {
            return remaining;
        }
    }
    
    private static int getDevPort(String remaining) {
        if (remaining.contains(":")) {
            return Integer.parseInt(remaining.split(":")[1]);
        } else {
            return 8080;
        }
    }
    
}
