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
package org.apache.camel.component.camunda;

import org.apache.camel.spi.Metadata;

public final class CamundaConstants {

    public static final String HEADER_PREFIX = "CamelCamunda";

    @Metadata(label = "producer", description = "Job key for the worker job", javaType = "long")
    public static final String JOB_KEY = HEADER_PREFIX + "JobKey";

    @Metadata(label = "producer", description = "Resource name for deploy operation", javaType = "String")
    public static final String RESOURCE_NAME = HEADER_PREFIX + "ResourceName";

    @Metadata(label = "producer", description = "Indicates if the operation was successful", javaType = "boolean")
    public static final String IS_SUCCESS = HEADER_PREFIX + "IsSuccess";

    @Metadata(label = "producer", description = "Error message if operation failed", javaType = "String")
    public static final String ERROR_MESSAGE = HEADER_PREFIX + "ErrorMessage";

    @Metadata(label = "producer", description = "Error code if operation failed", javaType = "String")
    public static final String ERROR_CODE = HEADER_PREFIX + "ErrorCode";

    @Metadata(label = "producer", description = "The process ID of a deployed process", javaType = "String")
    public static final String BPMN_PROCESS_ID = HEADER_PREFIX + "BPMNProcessId";

    @Metadata(label = "producer", description = "The version of a deployed process", javaType = "int")
    public static final String VERSION = HEADER_PREFIX + "Version";

    @Metadata(label = "producer", description = "The process definition key of a deployed process", javaType = "long")
    public static final String PROCESS_DEFINITION_KEY = HEADER_PREFIX + "ProcessDefinitionKey";

    /**
     * Exchange property set by completeJob/failJob/throwError producers to signal the worker consumer that the job was
     * already handled and auto-complete/auto-fail should be skipped.
     */
    public static final String JOB_HANDLED = "CamundaJobHandled";

    private CamundaConstants() {
    }
}
