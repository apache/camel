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
package org.apache.camel.component.grpc;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link GrpcEndpoint}.
 */
public class GrpcComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GrpcConfiguration config = new GrpcConfiguration();
        setProperties(config, parameters);

        // Extract service and package names from the full service name
        config.setServiceName(extractServiceName(remaining));
        config.setServicePackage(extractServicePackage(remaining));
        // Convert method name to the camel case style
        // This requires if method name as described inside .proto file directly
        config.setMethod(GrpcUtils.convertMethod2CamelCase(config.getMethod()));

        Endpoint endpoint = new GrpcEndpoint(uri, this, config);
        return endpoint;
    }

    private String extractServiceName(String service) {
        return service.substring(service.lastIndexOf(".") + 1);
    }

    private String extractServicePackage(String service) {
        return service.substring(0, service.lastIndexOf("."));
    }
}
