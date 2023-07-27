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
package org.apache.camel.main;

import org.apache.camel.Service;

/**
 * Factory for creating an embedded HTTP server for standalone (not Spring Boot or Quarkus).
 */
public interface MainHttpServerFactory {

    /**
     * Creates the embedded HTTP server
     *
     * @param  configuration server configuration
     * @return               the server as a {@link Service} to be managed by {@link org.apache.camel.CamelContext}.
     */
    Service newHttpServer(HttpServerConfigurationProperties configuration);
}
