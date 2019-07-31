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
package org.apache.camel.component.openstack.keystone;

import org.apache.camel.component.openstack.common.OpenstackConstants;

public final class KeystoneConstants extends OpenstackConstants {

    public static final String REGIONS = "regions";
    public static final String DOMAINS = "domains";
    public static final String PROJECTS = "projects";
    public static final String USERS = "users";
    public static final String GROUPS = "groups";

    public static final String DESCRIPTION = "description";
    public static final String DOMAIN_ID = "domainId";
    public static final String PARENT_ID = "parentId";

    public static final String PASSWORD = "password";
    public static final String EMAIL = "email";

    public static final String USER_ID = "userId";
    public static final String GROUP_ID = "groupId";

    public static final String ADD_USER_TO_GROUP = "addUserToGroup";
    public static final String CHECK_GROUP_USER = "checkUserGroup";
    public static final String REMOVE_USER_FROM_GROUP = "removeUserFromGroup";

    private KeystoneConstants() { }

}
