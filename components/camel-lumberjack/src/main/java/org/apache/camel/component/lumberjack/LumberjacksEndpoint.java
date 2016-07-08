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
package org.apache.camel.component.lumberjack;

import org.apache.camel.Processor;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.jsse.SSLContextParameters;

@UriEndpoint(scheme = "lumberjacks", title = "Lumberjacks", syntax = "lumberjacks:host:port", consumerClass = LumberjackConsumer.class, label = "log")
class LumberjacksEndpoint extends LumberjackEndpoint {
    @UriParam(description = "SSL configuration")
    private SSLContextParameters sslContextParameters;

    LumberjacksEndpoint(String endpointUri, LumberjackComponent component, String host, int port) {
        super(endpointUri, component, host, port);
    }

    @Override
    public LumberjackConsumer createConsumer(Processor processor) throws Exception {
        if (sslContextParameters == null) {
            throw new IllegalStateException("The sslContextParameters attribute must be defined for lumberjacks");
        }
        return new LumberjackConsumer(this, processor, host, port, sslContextParameters.createSSLContext(getCamelContext()));
    }

    @SuppressWarnings("unused")
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
}
