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
package org.apache.camel.test.infra.artemis.common;

public final class ArtemisProperties {
    public static final String SERVICE_ADDRESS = "artemis.service.address";

    public static final String ARTEMIS_EXTERNAL = "artemis.external";
    public static final String ARTEMIS_AUTHENTICATION_ENABLED = "artemis.authentication.enabled";
    public static final String ARTEMIS_USERNAME = "artemis.username";
    public static final String ARTEMIS_PASSWORD = "artemis.password";
    public static final String ARTEMIS_CONTAINER = "artemis.container";
    public static final String ARTEMIS_SSL_ENABLED = "artemis.ssl.enabled";
    public static final String ARTEMIS_SSL_KEYSTORE_PATH = "artemis.ssl.keystore.path";
    public static final String ARTEMIS_SSL_KEYSTORE_PASSWORD = "artemis.ssl.keystore.password";
    public static final String ARTEMIS_SSL_TRUSTSTORE_PATH = "artemis.ssl.truststore.path";
    public static final String ARTEMIS_SSL_TRUSTSTORE_PASSWORD = "artemis.ssl.truststore.password";

    private ArtemisProperties() {

    }
}
