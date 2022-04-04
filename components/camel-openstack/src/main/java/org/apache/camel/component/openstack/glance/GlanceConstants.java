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
package org.apache.camel.component.openstack.glance;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.spi.Metadata;

public final class GlanceConstants extends OpenstackConstants {

    @Metadata(description = "The number of flavor VCPU.", javaType = "org.openstack4j.model.image.DiskFormat")
    public static final String DISK_FORMAT = "diskFormat";
    @Metadata(description = "Size of RAM.", javaType = "org.openstack4j.model.image.ContainerFormat")
    public static final String CONTAINER_FORMAT = "containerFormat";
    @Metadata(description = "Image owner.", javaType = "String")
    public static final String OWNER = "owner";
    @Metadata(description = "Is public.", javaType = "Boolean")
    public static final String IS_PUBLIC = "isPublic";
    @Metadata(description = "Minimum ram.", javaType = "Long")
    public static final String MIN_RAM = "minRam";
    @Metadata(description = "Minimum disk.", javaType = "Long")
    public static final String MIN_DISK = "minDisk";
    @Metadata(description = "Size.", javaType = "Long")
    public static final String SIZE = "size";
    @Metadata(description = "Checksum.", javaType = "String")
    public static final String CHECKSUM = "checksum";

    public static final String RESERVE = "reserve";
    public static final String UPLOAD = "upload";

    private GlanceConstants() {
    }

}
