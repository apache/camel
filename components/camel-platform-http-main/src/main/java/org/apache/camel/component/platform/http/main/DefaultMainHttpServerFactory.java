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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConstants;
import org.apache.camel.main.MainHttpServerFactory;
import org.apache.camel.spi.annotations.JdkService;

@JdkService(MainConstants.PLATFORM_HTTP_SERVER)
public class DefaultMainHttpServerFactory implements CamelContextAware, MainHttpServerFactory {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Service newHttpServer(HttpServerConfigurationProperties configuration) {
        MainHttpServer server = new MainHttpServer();

        server.setCamelContext(camelContext);
        server.setHost(configuration.getHost());
        server.setPort(configuration.getPort());
        server.setPath(configuration.getPath());
        if (configuration.getMaxBodySize() != null) {
            server.setMaxBodySize(configuration.getMaxBodySize());
        }
        server.setUseGlobalSslContextParameters(configuration.isUseGlobalSslContextParameters());
        server.setDevConsoleEnabled(configuration.isDevConsoleEnabled());
        server.setHealthCheckEnabled(configuration.isHealthCheckEnabled());
        server.setUploadEnabled(configuration.isUploadEnabled());
        server.setUploadSourceDir(configuration.getUploadSourceDir());

        return server;
    }
}
