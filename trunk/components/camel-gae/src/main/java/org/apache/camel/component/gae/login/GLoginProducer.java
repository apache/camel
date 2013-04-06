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

import org.apache.camel.Exchange;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultProducer;

public class GLoginProducer extends DefaultProducer {

    public GLoginProducer(GLoginEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public GLoginEndpoint getEndpoint() {
        return (GLoginEndpoint)super.getEndpoint();
    }

    public OutboundBinding<GLoginEndpoint, GLoginData, GLoginData> getOutboundBinding() {
        return getEndpoint().getOutboundBinding();
    }

    public GLoginService getService() {
        return getEndpoint().getService();
    }

    /**
     * First obtains an authentication token and then exchanges the token against
     * an authorization cookie.
     *
     * @see GLoginBinding
     * @see GLoginServiceImpl
     */
    public void process(Exchange exchange) throws Exception {
        GLoginData data = getOutboundBinding().writeRequest(getEndpoint(), exchange, null);
        getService().authenticate(data);
        getOutboundBinding().readResponse(getEndpoint(), exchange, data);
        getService().authorize(data);
        getOutboundBinding().readResponse(getEndpoint(), exchange, data);
    }

}
