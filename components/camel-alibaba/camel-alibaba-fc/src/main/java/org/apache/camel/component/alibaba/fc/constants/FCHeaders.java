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
package org.apache.camel.component.alibaba.fc.constants;

import org.apache.camel.spi.Metadata;

public final class FCHeaders {

    @Metadata(label = "producer",
              description = "The name of the service that contains the function. This header replaces the serviceName option."
                            + " If this header is not set, an exchange property of the same name replaces the serviceName"
                            + " option.",
              javaType = "String")
    public static final String SERVICE_NAME = FCProperties.SERVICE_NAME;

    @Metadata(label = "producer",
              description = "The name of the function. This header replaces the functionName option. If this header is not set,"
                            + " an exchange property of the same name replaces the functionName option.",
              javaType = "String")
    public static final String FUNCTION_NAME = FCProperties.FUNCTION_NAME;

    @Metadata(label = "producer",
              description = "The version or alias of the service. This header replaces the qualifier option. If this header is"
                            + " not set, an exchange property of the same name replaces the qualifier option.",
              javaType = "String")
    public static final String QUALIFIER = FCProperties.QUALIFIER;

    @Metadata(label = "producer",
              description = "The request ID that Function Compute returns in the response to invokeFunction. The producer sets"
                            + " it for invokeFunction, unless the exchange fails.",
              javaType = "String")
    public static final String REQUEST_ID = FCProperties.REQUEST_ID;

    @Metadata(label = "producer",
              description = "The HTTP status code of the response to the operation. The producer sets it, unless the exchange"
                            + " fails.",
              javaType = "Integer")
    public static final String STATUS_CODE = "CamelAlibabaFcStatusCode";

    private FCHeaders() {
    }
}
