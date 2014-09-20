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
package org.apache.camel.component.ahc.ws;

import java.net.URI;

import org.apache.camel.component.ahc.AhcComponent;
import org.apache.camel.component.ahc.AhcEndpoint;

/**
 *  Defines the <a href="http://camel.apache.org/ws.html">WebSocket Client Component</a>
 */
public class WsComponent extends AhcComponent {
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.ahc.AhcComponent#createAddressUri(java.lang.String, java.lang.String)
     */
    @Override
    protected String createAddressUri(String uri, String remaining) {
        // remove "ahc-"
        return uri.substring(4);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.ahc.AhcComponent#createAhcEndpoint(java.lang.String, org.apache.camel.component.ahc.AhcComponent, java.net.URI)
     */
    @Override
    protected AhcEndpoint createAhcEndpoint(String endpointUri, AhcComponent component, URI httpUri) {
        return new WsEndpoint(endpointUri, (WsComponent)component);
    }

}
