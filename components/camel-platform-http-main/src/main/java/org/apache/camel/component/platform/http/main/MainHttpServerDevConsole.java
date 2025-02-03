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
package org.apache.camel.component.platform.http.main;

import java.util.Map;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "main-http-server", displayName = "Main HTTP Server", description = "Camel Main HTTP Server")
public class MainHttpServerDevConsole extends AbstractDevConsole {

    public MainHttpServerDevConsole() {
        super("camel", "main-http-server", "Main HTTP Server", "Camel Main HTTP Server");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        MainHttpServer server = getCamelContext().hasService(MainHttpServer.class);
        if (server != null) {
            String p = server.getPath();
            if (p != null && p.startsWith("/")) {
                p = p.substring(1);
            }
            String url = String.format("%s:%s%s", server.getHost(), server.getPort(), p);
            sb.append(String.format("    Server: http://%s", url));
            if (server.getMaxBodySize() != null) {
                sb.append(String.format("\n    Max Body Size: %s", server.getMaxBodySize()));
            }
            sb.append(String.format("\n    File Upload Enabled: %b", server.isFileUploadEnabled()));
            sb.append(String.format("\n    File Upload Dir: %s", server.getFileUploadDirectory()));
            sb.append(String.format("\n    Use Global SSL ContextParameters: %s", server.isUseGlobalSslContextParameters()));
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        MainHttpServer server = getCamelContext().hasService(MainHttpServer.class);
        if (server != null) {
            root.put("host", server.getHost());
            root.put("port", server.getPort());
            root.put("path", server.getPath());
            if (server.getMaxBodySize() != null) {
                root.put("maxBodySize", server.getMaxBodySize());
            }
            root.put("fileUploadEnabled", server.isFileUploadEnabled());
            if (server.getFileUploadDirectory() != null) {
                root.put("fileUploadDirectory", server.getFileUploadDirectory());
            }
            root.put("useGlobalSslContextParameters", server.isUseGlobalSslContextParameters());
        }

        return root;
    }
}
