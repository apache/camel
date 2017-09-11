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
package org.apache.camel.model.cloud;

public final class ServiceCallDefinitionConstants {
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/cloud/";
    public static final String DEFAULT_COMPONENT = "http4";
    public static final String DEFAULT_SERVICE_CALL_CONFIG_ID = "service-call-configuration";
    public static final String DEFAULT_SERVICE_CALL_EXPRESSION_ID = "service-call-expression";
    public static final String DEFAULT_SERVICE_DISCOVERY_ID = "service-discovery";
    public static final String DEFAULT_SERVICE_FILTER_ID = "service-filter";
    public static final String DEFAULT_SERVICE_CHOOSER_ID = "service-chooser";
    public static final String DEFAULT_LOAD_BALANCER_ID = "load-balancer";

    private ServiceCallDefinitionConstants() {
    }
}
