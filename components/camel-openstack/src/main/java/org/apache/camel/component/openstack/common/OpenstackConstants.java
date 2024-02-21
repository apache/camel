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
package org.apache.camel.component.openstack.common;

import org.apache.camel.spi.Metadata;

/**
 * General camel-openstack component constants. The main purpose for this class is to avoid duplication general
 * constants in each submodule.
 */
public class OpenstackConstants {

    // The schemes
    public static final String SCHEME_CINDER = "openstack-cinder";
    public static final String SCHEME_GLANCE = "openstack-glance";
    public static final String SCHEME_KEYSTONE = "openstack-keystone";
    public static final String SCHEME_NEUTRON = "openstack-neutron";
    public static final String SCHEME_NOVA = "openstack-nova";
    public static final String SCHEME_SWIFT = "openstack-swift";

    @Metadata(description = "The operation to perform.", javaType = "String")
    public static final String OPERATION = "operation";
    @Metadata(description = "The ID.", javaType = "String")
    public static final String ID = "ID";
    @Metadata(description = "The name.", javaType = "String")
    public static final String NAME = "name";
    @Metadata(description = "The description.", javaType = "String", applicableFor = { SCHEME_CINDER })
    public static final String DESCRIPTION = "description";
    @Metadata(description = "The image properties.", javaType = "Map<String, String>", applicableFor = SCHEME_GLANCE)
    public static final String PROPERTIES = "properties";

    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String GET_ALL = "getAll";
    public static final String GET = "get";
    public static final String DELETE = "delete";

    protected OpenstackConstants() {
    }
}
