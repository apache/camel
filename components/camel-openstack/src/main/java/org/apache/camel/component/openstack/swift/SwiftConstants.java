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

public final class SwiftConstants extends OpenstackConstants {

    public static final String SWIFT_SUBSYSTEM_OBJECTS = "objects";
    public static final String SWIFT_SUBSYSTEM_CONTAINERS = "containers";

    public static final String CONTAINER_NAME = "containerName";
    public static final String OBJECT_NAME = "objectName";

    public static final String LIMIT = "limit";
    public static final String MARKER = "marker";
    public static final String END_MARKER = "end_marker";
    public static final String DELIMITER = "delimiter";
    public static final String PATH = "path";

    public static final String GET_METADATA = "getMetadata";
    public static final String CREATE_UPDATE_METADATA = "createUpdateMetadata";
    public static final String DELETE_METADATA = "deleteMetadata";

    private SwiftConstants() { }

}
