/*
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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * The class is the Camel component for the Lumberjack server
 */
@Component("lumberjack")
public class LumberjackComponent extends DefaultComponent implements SSLContextParametersAware {
    static final int DEFAULT_PORT = 5044;

    @Metadata(label = "security")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public LumberjackComponent() {
        this(LumberjackEndpoint.class);
    }

    protected LumberjackComponent(Class<? extends LumberjackEndpoint> endpointClass) {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Extract host and port
        String host;
        int port;
        int separatorIndex = remaining.indexOf(':');
        if (separatorIndex >= 0) {
            host = remaining.substring(0, separatorIndex);
            port = Integer.parseInt(remaining.substring(separatorIndex + 1));
        } else {
            host = remaining;
            port = DEFAULT_PORT;
        }

        // Create the endpoint
        LumberjackEndpoint answer = new LumberjackEndpoint(uri, this, host, port);
        setProperties(answer, parameters);

        if (answer.getSslContextParameters() == null) {
            answer.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return answer;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Sets the default SSL configuration to use for all the endpoints. You can also configure it directly at
     * the endpoint level.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

}
