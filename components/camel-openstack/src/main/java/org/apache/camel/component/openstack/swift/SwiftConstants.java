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
package org.apache.camel.component.openstack.swift;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.spi.Metadata;
import org.openstack4j.model.storage.object.SwiftHeaders;

public final class SwiftConstants extends OpenstackConstants {

    public static final String SWIFT_SUBSYSTEM_OBJECTS = "objects";
    public static final String SWIFT_SUBSYSTEM_CONTAINERS = "containers";

    @Metadata(label = "object container", description = "The container name.", javaType = "String")
    public static final String CONTAINER_NAME = "containerName";
    @Metadata(label = "object", description = "The object name.", javaType = "String")
    public static final String OBJECT_NAME = "objectName";

    @Metadata(label = "container", description = "Container metadata prefix.", javaType = "Map<String, String>")
    public static final String CONTAINER_METADATA_PREFIX = SwiftHeaders.CONTAINER_METADATA_PREFIX;
    @Metadata(label = "container", description = "Versions location.", javaType = "String")
    public static final String VERSIONS_LOCATION = SwiftHeaders.VERSIONS_LOCATION;
    @Metadata(label = "container", description = "ACL - container read.", javaType = "String")
    public static final String CONTAINER_READ = SwiftHeaders.CONTAINER_READ;
    @Metadata(label = "container", description = "ACL - container write.", javaType = "String")
    public static final String CONTAINER_WRITE = SwiftHeaders.CONTAINER_WRITE;

    @Metadata(label = "container", description = "List options - limit.", javaType = "Integer")
    public static final String LIMIT = "limit";
    @Metadata(label = "container", description = "List options - marker.", javaType = "String")
    public static final String MARKER = "marker";
    @Metadata(label = "container", description = "List options - end marker.", javaType = "String")
    public static final String END_MARKER = "end_marker";
    @Metadata(label = "container", description = "List options - delimiter.", javaType = "Character")
    public static final String DELIMITER = "delimiter";
    @Metadata(label = "container object", description = "The path.", javaType = "String")
    public static final String PATH = "path";

    public static final String GET_METADATA = "getMetadata";
    public static final String CREATE_UPDATE_METADATA = "createUpdateMetadata";
    public static final String DELETE_METADATA = "deleteMetadata";

    private SwiftConstants() {
    }

}
