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
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsIAMOperation";
    @Metadata(description = "The username for the user you want to manage", javaType = "String")
    String USERNAME = "CamelAwsIAMUsername";
    @Metadata(description = "The accessKey you want to manage", javaType = "String")
    String ACCESS_KEY_ID = "CamelAwsIAMAccessKeyID";
    @Metadata(description = "The Status of the AccessKey you want to set, possible value are active and inactive",
              javaType = "String")
    String ACCESS_KEY_STATUS = "CamelAwsIAMAccessKeyStatus";
    @Metadata(description = "The name of an AWS IAM Group", javaType = "String")
    String GROUP_NAME = "CamelAwsIAMGroupName";
    @Metadata(description = "The path of an AWS IAM Group", javaType = "String")
    String GROUP_PATH = "CamelAwsIAMGroupPath";

    // Pagination support
    @Metadata(description = "The marker to use for pagination in list operations", javaType = "String")
    String MARKER = "CamelAwsIAMMarker";
    @Metadata(description = "The maximum number of items to return in list operations", javaType = "Integer")
    String MAX_ITEMS = "CamelAwsIAMMaxItems";

    // Response metadata
    @Metadata(description = "Whether the list response is truncated (has more results)", javaType = "Boolean")
    String IS_TRUNCATED = "CamelAwsIAMIsTruncated";
    @Metadata(description = "The marker to use for the next page of results", javaType = "String")
    String NEXT_MARKER = "CamelAwsIAMNextMarker";
    @Metadata(description = "The ARN of the created or retrieved user", javaType = "String")
    String USER_ARN = "CamelAwsIAMUserArn";
    @Metadata(description = "The ID of the created or retrieved user", javaType = "String")
    String USER_ID = "CamelAwsIAMUserId";
    @Metadata(description = "The ARN of the created or retrieved group", javaType = "String")
    String GROUP_ARN = "CamelAwsIAMGroupArn";
    @Metadata(description = "The ID of the created or retrieved group", javaType = "String")
    String GROUP_ID = "CamelAwsIAMGroupId";

    // Role constants
    @Metadata(description = "The name of an AWS IAM Role", javaType = "String")
    String ROLE_NAME = "CamelAwsIAMRoleName";
    @Metadata(description = "The path of an AWS IAM Role", javaType = "String")
    String ROLE_PATH = "CamelAwsIAMRolePath";
    @Metadata(description = "The assume role policy document for the role", javaType = "String")
    String ASSUME_ROLE_POLICY_DOCUMENT = "CamelAwsIAMAssumeRolePolicyDocument";
    @Metadata(description = "The ARN of the created or retrieved role", javaType = "String")
    String ROLE_ARN = "CamelAwsIAMRoleArn";
    @Metadata(description = "The ID of the created or retrieved role", javaType = "String")
    String ROLE_ID = "CamelAwsIAMRoleId";
    @Metadata(description = "The description of an AWS IAM Role", javaType = "String")
    String ROLE_DESCRIPTION = "CamelAwsIAMRoleDescription";

    // Policy constants
    @Metadata(description = "The name of an AWS IAM Policy", javaType = "String")
    String POLICY_NAME = "CamelAwsIAMPolicyName";
    @Metadata(description = "The path of an AWS IAM Policy", javaType = "String")
    String POLICY_PATH = "CamelAwsIAMPolicyPath";
    @Metadata(description = "The policy document", javaType = "String")
    String POLICY_DOCUMENT = "CamelAwsIAMPolicyDocument";
    @Metadata(description = "The ARN of an AWS IAM Policy", javaType = "String")
    String POLICY_ARN = "CamelAwsIAMPolicyArn";
    @Metadata(description = "The ID of an AWS IAM Policy", javaType = "String")
    String POLICY_ID = "CamelAwsIAMPolicyId";
    @Metadata(description = "The description of an AWS IAM Policy", javaType = "String")
    String POLICY_DESCRIPTION = "CamelAwsIAMPolicyDescription";

    // Instance profile constants
    @Metadata(description = "The name of an AWS IAM Instance Profile", javaType = "String")
    String INSTANCE_PROFILE_NAME = "CamelAwsIAMInstanceProfileName";
    @Metadata(description = "The path of an AWS IAM Instance Profile", javaType = "String")
    String INSTANCE_PROFILE_PATH = "CamelAwsIAMInstanceProfilePath";
    @Metadata(description = "The ARN of an AWS IAM Instance Profile", javaType = "String")
    String INSTANCE_PROFILE_ARN = "CamelAwsIAMInstanceProfileArn";
    @Metadata(description = "The ID of an AWS IAM Instance Profile", javaType = "String")
    String INSTANCE_PROFILE_ID = "CamelAwsIAMInstanceProfileId";
}
