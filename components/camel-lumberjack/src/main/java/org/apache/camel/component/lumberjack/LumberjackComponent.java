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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * The class is the Camel component for the Lumberjack server
 */
public class LumberjackComponent extends UriEndpointComponent {
    static final int DEFAULT_PORT = 5044;

    public LumberjackComponent() {
        this(LumberjackEndpoint.class);
    }

    protected LumberjackComponent(Class<? extends LumberjackEndpoint> endpointClass) {
        super(endpointClass);
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

        Endpoint answer = createEndpoint(uri, host, port);
        setProperties(answer, parameters);
        return answer;
    }

    protected LumberjackEndpoint createEndpoint(String uri, String host, int port) {
        return new LumberjackEndpoint(uri, this, host, port);
    }
}
