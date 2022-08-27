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
package org.apache.camel.spi;

import org.apache.camel.Service;

/**
 * Factory for creating connector to CLI tooling.
 * <p/>
 * Such as a local {@link CliConnector} that allows Camel CLI to manage local running Camel integrations.
 */
public interface CliConnectorFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "cli-connector-factory";

    /**
     * To enable CLI connector.
     */
    void setEnabled(boolean enabled);

    /**
     * Whether CLI connector is enabled.
     */
    boolean isEnabled();

    /**
     * What runtime platform is in use, such as camel-jbang, camel-spring-boot, or camel-quarkus etc.
     */
    void setRuntime(String runtime);

    /**
     * What runtime platform is in use, such as camel-jbang, camel-spring-boot, or camel-quarkus etc.
     */
    String getRuntime();

    /**
     * What runtime platform version is in use.
     */
    void setRuntimeVersion(String version);

    /**
     * What runtime platform version is in use.
     */
    String getRuntimeVersion();

    /**
     * The main class used by the runtime to start.
     */
    void setRuntimeStartClass(String className);

    /**
     * The main class used by the runtime to start.
     */
    String getRuntimeStartClass();

    /**
     * Creates the connector which will be added as a {@link Service} to {@link org.apache.camel.CamelContext} as the
     * lifecycle to start and stop the connector.
     */
    CliConnector createConnector();

}
