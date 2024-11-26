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

package org.apache.camel.test.infra.opensearch.common;

import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;

public final class OpenSearchProperties {
    public static final String OPEN_SEARCH_HOST = "opensearch.host";
    public static final String OPEN_SEARCH_PORT = "opensearch.port";
    public static final String OPEN_SEARCH_USERNAME = "opensearch.username";
    public static final String OPEN_SEARCH_PASSWORD = "opensearch.password";
    public static final String OPEN_SEARCH_CONTAINER = "opensearch.container";
    public static final String OPEN_SEARCH_CONTAINER_STARTUP
            = OPEN_SEARCH_CONTAINER + ContainerEnvironmentUtil.STARTUP_ATTEMPTS_PROPERTY;

    private OpenSearchProperties() {

    }
}
