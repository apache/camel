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

/**
 * Headers used by the Apicurio Registry component.
 */
public final class ApicurioRegistryConstants {

    @Metadata(description = "The operation to perform. Overrides the operation configured on the endpoint.",
              javaType = "org.apache.camel.component.apicurioregistry.ApicurioRegistryOperations")
    public static final String OPERATION = "CamelApicurioRegistryOperation";

    @Metadata(description = "The group id of the artifact. Overrides the groupId configured on the endpoint.",
              javaType = "String")
    public static final String GROUP_ID = "CamelApicurioRegistryGroupId";

    @Metadata(description = "The id of the artifact. Overrides the artifactId configured on the endpoint.",
              javaType = "String")
    public static final String ARTIFACT_ID = "CamelApicurioRegistryArtifactId";

    @Metadata(description = "The type of the artifact (for example AVRO, JSON, PROTOBUF). Overrides the artifactType configured on the endpoint.",
              javaType = "String")
    public static final String ARTIFACT_TYPE = "CamelApicurioRegistryArtifactType";

    @Metadata(description = "The version of the artifact. Overrides the version configured on the endpoint.",
              javaType = "String")
    public static final String VERSION = "CamelApicurioRegistryVersion";

    @Metadata(description = "The content type of the artifact content being sent (for example application/json).",
              javaType = "String")
    public static final String CONTENT_TYPE = "CamelApicurioRegistryContentType";

    private ApicurioRegistryConstants() {
    }
}
