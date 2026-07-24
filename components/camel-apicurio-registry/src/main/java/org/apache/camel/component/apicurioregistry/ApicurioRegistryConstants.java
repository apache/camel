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
package org.apache.camel.component.apicurioregistry;

import org.apache.camel.spi.Metadata;

public interface ApicurioRegistryConstants {

    @Metadata(description = "The operation to perform", javaType = "String")
    String HEADER_OPERATION = "CamelApicurioRegistryOperation";

    @Metadata(description = "The artifact group ID", javaType = "String")
    String HEADER_GROUP_ID = "CamelApicurioRegistryGroupId";

    @Metadata(description = "The artifact ID", javaType = "String")
    String HEADER_ARTIFACT_ID = "CamelApicurioRegistryArtifactId";

    @Metadata(description = "The artifact type (e.g. AVRO, PROTOBUF, JSON, OPENAPI)", javaType = "String")
    String HEADER_ARTIFACT_TYPE = "CamelApicurioRegistryArtifactType";

    @Metadata(description = "The artifact version expression", javaType = "String")
    String HEADER_VERSION = "CamelApicurioRegistryVersion";

    @Metadata(description = "The artifact name", javaType = "String")
    String HEADER_ARTIFACT_NAME = "CamelApicurioRegistryArtifactName";

    @Metadata(description = "The artifact description", javaType = "String")
    String HEADER_ARTIFACT_DESCRIPTION = "CamelApicurioRegistryArtifactDescription";

    @Metadata(description = "Behavior when artifact already exists (FAIL, CREATE_VERSION, FIND_OR_CREATE_VERSION)",
              javaType = "String")
    String HEADER_IF_EXISTS = "CamelApicurioRegistryIfExists";

    @Metadata(description = "The content type of the artifact", javaType = "String")
    String HEADER_CONTENT_TYPE = "CamelApicurioRegistryContentType";

    @Metadata(description = "Whether the operation is a dry run", javaType = "Boolean")
    String HEADER_DRY_RUN = "CamelApicurioRegistryDryRun";

    @Metadata(label = "consumer", description = "The version global ID", javaType = "Long")
    String HEADER_GLOBAL_ID = "CamelApicurioRegistryGlobalId";

    @Metadata(label = "consumer", description = "The content ID", javaType = "Long")
    String HEADER_CONTENT_ID = "CamelApicurioRegistryContentId";

    @Metadata(label = "consumer", description = "The version state", javaType = "String")
    String HEADER_VERSION_STATE = "CamelApicurioRegistryVersionState";

    @Metadata(description = "Whether validation passed", javaType = "Boolean")
    String HEADER_VALIDATION_RESULT = "CamelApicurioRegistryValidationResult";

    @Metadata(description = "Validation error details", javaType = "String")
    String HEADER_VALIDATION_ERRORS = "CamelApicurioRegistryValidationErrors";

    String OPERATION_CREATE_ARTIFACT = "createArtifact";
    String OPERATION_UPDATE_ARTIFACT = "updateArtifact";
    String OPERATION_DELETE_ARTIFACT = "deleteArtifact";
    String OPERATION_GET_ARTIFACT_CONTENT = "getArtifactContent";
    String OPERATION_GET_ARTIFACT_METADATA = "getArtifactMetadata";
    String OPERATION_SEARCH_ARTIFACTS = "searchArtifacts";
    String OPERATION_LIST_VERSIONS = "listVersions";
    String OPERATION_CREATE_GROUP = "createGroup";
    String OPERATION_TEST_COMPATIBILITY = "testCompatibility";
    String OPERATION_VALIDATE = "validate";
}
