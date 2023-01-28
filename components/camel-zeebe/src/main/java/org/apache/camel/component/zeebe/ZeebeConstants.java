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
package org.apache.camel.component.zeebe;

import org.apache.camel.spi.Metadata;

public final class ZeebeConstants {

    public static final String HEADER_PREFIX = "CamelZeebe";

    public static final String DEFAULT_GATEWAY_HOST = "localhost";
    public static final int DEFAULT_GATEWAY_PORT = 26500;

    @Metadata(label = "producer", description = "The name of the resource.")
    public static final String RESOURCE_NAME = HEADER_PREFIX + "ResourceName";

    @Metadata(label = "producer", description = "True if the operation was successful.")
    public static final String IS_SUCCESS = HEADER_PREFIX + "IsSuccess";

    @Metadata(label = "producer", description = "In case of an error, the error message.")
    public static final String ERROR_MESSAGE = HEADER_PREFIX + "ErrorMessage";

    @Metadata(label = "producer", description = "In case of an error, the error code if available.")
    public static final String ERROR_CODE = HEADER_PREFIX + "ErrorCode";

    @Metadata(label = "producer", description = "The process ID of a deployed process.")
    public static final String BPMN_PROCESS_ID = HEADER_PREFIX + "BPMNProcessId";

    @Metadata(label = "producer", description = "The version of a deployed process.")
    public static final String VERSION = HEADER_PREFIX + "Version";

    @Metadata(label = "producer", description = "The process definition key of a deployed process.")
    public static final String PROCESS_DEFINITION_KEY = HEADER_PREFIX + "ProcessDefinitionKey";

    private ZeebeConstants() {
    }
}
