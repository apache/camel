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

package org.apache.camel.test.infra.elasticsearch.common;

import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;

public final class ElasticSearchProperties {
    public static final String ELASTIC_SEARCH_HOST = "elasticsearch.host";
    public static final String ELASTIC_SEARCH_PORT = "elasticsearch.port";
    public static final String ELASTIC_SEARCH_CERTIFICATE_PATH = "elasticsearch.certificate.path";
    public static final String ELASTIC_SEARCH_USERNAME = "elasticsearch.username";
    public static final String ELASTIC_SEARCH_PASSWORD = "elasticsearch.password";
    public static final String ELASTIC_SEARCH_CONTAINER = "elasticsearch.container";
    public static final String ELASTIC_SEARCH_CONTAINER_STARTUP
            = ELASTIC_SEARCH_CONTAINER + ContainerEnvironmentUtil.STARTUP_ATTEMPTS_PROPERTY;

    private ElasticSearchProperties() {

    }
}
