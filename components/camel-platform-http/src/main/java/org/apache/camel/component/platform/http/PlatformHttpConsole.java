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
package org.apache.camel.component.platform.http;

import java.util.Map;
import java.util.Set;

import org.apache.camel.impl.console.AbstractDevConsole;
import org.apache.camel.spi.annotations.DevConsole;

@DevConsole("platform-http")
public class PlatformHttpConsole extends AbstractDevConsole {

    public PlatformHttpConsole() {
        super("camel", "platform-http", "Platform HTTP", "Embedded HTTP Server");
    }

    @Override
    protected Object doCall(MediaType mediaType, Map<String, Object> options) {
        // only text is supported
        StringBuilder sb = new StringBuilder();

        PlatformHttpComponent http = getCamelContext().getComponent("platform-http", PlatformHttpComponent.class);
        if (http != null) {
            String server = "http://0.0.0.0";
            int port = http.getEngine().getServerPort();
            if (port > 0) {
                server += ":" + port;
            }
            Set<HttpEndpointModel> models = http.getHttpEndpoints();
            for (HttpEndpointModel model : models) {
                if (model.getVerbs() != null) {
                    sb.append(String.format("    %s%s (%s)\n", server, model.getUri(), model.getVerbs()));
                } else {
                    sb.append(String.format("    %s%s\n", server, model.getUri()));
                }
            }
        }

        return sb.toString();
    }

}
