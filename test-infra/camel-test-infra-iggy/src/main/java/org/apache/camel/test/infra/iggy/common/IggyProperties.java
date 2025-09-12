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

package org.apache.camel.test.infra.iggy.common;

public final class IggyProperties {
    public static final String IGGY_CONTAINER = "iggy.container";
    public static final int DEFAULT_TCP_PORT = 8090;
    public static final String DEFAULT_USERNAME = "iggy";
    public static final String DEFAULT_PASSWORD = "Secret123!";

    public static final String IGGY_SERVICE_HOST = "iggy.service.host";
    public static final String IGGY_SERVICE_PORT = "iggy.service.port";
    public static final String IGGY_SERVICE_USERNAME = "iggy.service.username";
    public static final String IGGY_SERVICE_PASSWORD = "iggy.service.password";

    private IggyProperties() {

    }
}
