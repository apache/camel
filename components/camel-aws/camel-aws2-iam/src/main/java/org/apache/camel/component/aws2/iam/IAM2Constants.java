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
}
