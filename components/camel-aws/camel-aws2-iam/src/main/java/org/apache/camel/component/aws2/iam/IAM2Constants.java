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
package org.apache.camel.component.aws2.iam;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS IAM module
 */
public interface IAM2Constants {
    @Metadata(description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "String")
    String OPERATION = "CamelAwsIAMOperation";
    @Metadata(description = "The name of the IAM user. createUser, deleteUser, getUser, addUserToGroup, removeUserFromGroup, attachUserPolicy and detachUserPolicy require it. listAccessKeys, createAccessKey, deleteAccessKey and updateAccessKey use it to select the user of the access key; when not set, IAM takes the user of the access key that signs the request.",
              javaType = "String")
    String USERNAME = "CamelAwsIAMUsername";
    @Metadata(description = "The ID of the access key that deleteAccessKey deletes or updateAccessKey updates. Both operations require it.",
              javaType = "String")
    String ACCESS_KEY_ID = "CamelAwsIAMAccessKeyID";
    @Metadata(description = "The status that updateAccessKey sets on the access key: Active or Inactive, in any case. updateAccessKey requires it, and another value fails the exchange.",
              javaType = "String")
    String ACCESS_KEY_STATUS = "CamelAwsIAMAccessKeyStatus";
    @Metadata(description = "The name of the IAM group. createGroup, deleteGroup, addUserToGroup, removeUserFromGroup, attachGroupPolicy and detachGroupPolicy require it.",
              javaType = "String")
    String GROUP_NAME = "CamelAwsIAMGroupName";
    @Metadata(description = "The path of the group that createGroup creates, such as /division_abc/. When not set, IAM uses the path /.",
              javaType = "String")
    String GROUP_PATH = "CamelAwsIAMGroupPath";

    // Pagination support
    @Metadata(description = "The marker of the CamelAwsIAMNextMarker header of the previous response, to get the next page of results of a list operation: listAccessKeys, listUsers, listGroups, listRoles, listPolicies or listInstanceProfiles.",
              javaType = "String")
    String MARKER = "CamelAwsIAMMarker";
    @Metadata(description = "The maximum number of items in one page of results of a list operation: listAccessKeys, listUsers, listGroups, listRoles, listPolicies or listInstanceProfiles. IAM accepts 1 to 1000, and returns at most 100 items when not set.",
              javaType = "Integer")
    String MAX_ITEMS = "CamelAwsIAMMaxItems";

    // Response metadata
    @Metadata(description = "Whether a list operation has more results than it returned. When true, the CamelAwsIAMNextMarker header gives the marker of the next page.",
              javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsIAMIsTruncated";
    @Metadata(description = "The marker that a list operation sets when it has more results. Send it in the CamelAwsIAMMarker header to get the next page.",
              javaType = "String")
    String NEXT_MARKER = "CamelAwsIAMNextMarker";
    @Metadata(description = "The ARN of the user that createUser created or getUser returned.",
              javaType = "String")
    String USER_ARN = "CamelAwsIAMUserArn";
    @Metadata(description = "The unique ID that IAM gave to the user that createUser created or getUser returned.",
              javaType = "String")
    String USER_ID = "CamelAwsIAMUserId";
    @Metadata(description = "The ARN of the group that createGroup created.",
              javaType = "String")
    String GROUP_ARN = "CamelAwsIAMGroupArn";
    @Metadata(description = "The unique ID that IAM gave to the group that createGroup created.",
              javaType = "String")
    String GROUP_ID = "CamelAwsIAMGroupId";

    // Role constants
    @Metadata(description = "The name of the IAM role. createRole, deleteRole, getRole, attachRolePolicy, detachRolePolicy, addRoleToInstanceProfile and removeRoleFromInstanceProfile require it.",
              javaType = "String")
    String ROLE_NAME = "CamelAwsIAMRoleName";
    @Metadata(description = "The path of the role that createRole creates. When not set, IAM uses the path /.",
              javaType = "String")
    String ROLE_PATH = "CamelAwsIAMRolePath";
    @Metadata(description = "The trust policy of the role that createRole creates, as a JSON policy document that says which principals can assume the role. createRole requires it.",
              javaType = "String")
    String ASSUME_ROLE_POLICY_DOCUMENT = "CamelAwsIAMAssumeRolePolicyDocument";
    @Metadata(description = "The ARN of the role that createRole created or getRole returned.",
              javaType = "String")
    String ROLE_ARN = "CamelAwsIAMRoleArn";
    @Metadata(description = "The unique ID that IAM gave to the role that createRole created or getRole returned.",
              javaType = "String")
    String ROLE_ID = "CamelAwsIAMRoleId";
    @Metadata(description = "The description of the role that createRole creates.",
              javaType = "String")
    String ROLE_DESCRIPTION = "CamelAwsIAMRoleDescription";

    // Policy constants
    @Metadata(description = "The name of the managed policy that createPolicy creates. createPolicy requires it.",
              javaType = "String")
    String POLICY_NAME = "CamelAwsIAMPolicyName";
    @Metadata(description = "The path of the managed policy that createPolicy creates. When not set, IAM uses the path /.",
              javaType = "String")
    String POLICY_PATH = "CamelAwsIAMPolicyPath";
    @Metadata(description = "The JSON policy document of the managed policy that createPolicy creates, with the permissions of the policy. createPolicy requires it.",
              javaType = "String")
    String POLICY_DOCUMENT = "CamelAwsIAMPolicyDocument";
    @Metadata(description = "The ARN of the managed policy. deletePolicy, getPolicy and the operations that attach a policy to or detach it from a user, group or role require it. createPolicy and getPolicy set it to the ARN of the created or returned policy.",
              javaType = "String")
    String POLICY_ARN = "CamelAwsIAMPolicyArn";
    @Metadata(description = "The unique ID that IAM gave to the managed policy that createPolicy created or getPolicy returned.",
              javaType = "String")
    String POLICY_ID = "CamelAwsIAMPolicyId";
    @Metadata(description = "The description of the managed policy that createPolicy creates.",
              javaType = "String")
    String POLICY_DESCRIPTION = "CamelAwsIAMPolicyDescription";

    // Instance profile constants
    @Metadata(description = "The name of the instance profile. createInstanceProfile, deleteInstanceProfile, getInstanceProfile, addRoleToInstanceProfile and removeRoleFromInstanceProfile require it.",
              javaType = "String")
    String INSTANCE_PROFILE_NAME = "CamelAwsIAMInstanceProfileName";
    @Metadata(description = "The path of the instance profile that createInstanceProfile creates. When not set, IAM uses the path /.",
              javaType = "String")
    String INSTANCE_PROFILE_PATH = "CamelAwsIAMInstanceProfilePath";
    @Metadata(description = "The ARN of the instance profile that createInstanceProfile created or getInstanceProfile returned.",
              javaType = "String")
    String INSTANCE_PROFILE_ARN = "CamelAwsIAMInstanceProfileArn";
    @Metadata(description = "The unique ID that IAM gave to the instance profile that createInstanceProfile created or getInstanceProfile returned.",
              javaType = "String")
    String INSTANCE_PROFILE_ID = "CamelAwsIAMInstanceProfileId";
}
