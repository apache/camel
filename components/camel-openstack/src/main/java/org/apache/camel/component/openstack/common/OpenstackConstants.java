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

/**
 * General camel-openstack component constants.
 * The main purpose for this class is to avoid duplication general constants in each submodule.
 */
public class OpenstackConstants {

    public static final String OPERATION = "operation";
    public static final String ID = "ID";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String PROPERTIES = "properties";

    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String GET_ALL = "getAll";
    public static final String GET = "get";
    public static final String DELETE = "delete";

    protected OpenstackConstants() { }
}
