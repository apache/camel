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
package org.apache.camel.component.openstack.glance;

import org.apache.camel.component.openstack.common.OpenstackConstants;

public final class GlanceConstants extends OpenstackConstants {

    public static final String DISK_FORMAT = "diskFormat";
    public static final String CONTAINER_FORMAT = "containerFormat";
    public static final String OWNER = "owner";
    public static final String IS_PUBLIC = "isPublic";
    public static final String MIN_RAM = "minRam";
    public static final String MIN_DISK = "minDisk";
    public static final String SIZE = "size";
    public static final String CHECKSUM = "checksum";

    public static final String RESERVE = "reserve";
    public static final String UPLOAD = "upload";

    private GlanceConstants() { }

}
