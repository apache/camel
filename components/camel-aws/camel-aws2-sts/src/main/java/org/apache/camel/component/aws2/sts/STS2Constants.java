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
package org.apache.camel.component.aws2.sts;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 STS module
 */
public interface STS2Constants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsStsOperation";
    @Metadata(description = "The Amazon Resource Name (ARN) of the role to assume.", javaType = "String")
    String ROLE_ARN = "CamelAwsStsRoleArn";
    @Metadata(description = "An identifier for the assumed role session.", javaType = "String")
    String ROLE_SESSION_NAME = "CamelAwsStsRoleSessionName";
    @Metadata(description = "The name of the federated user.", javaType = "String")
    String FEDERATED_NAME = "CamelAwsStsFederatedName";
    @Metadata(description = "The duration, in seconds, of the role session. It could go from 900 seconds, to 1 to 12 hours (dependent on administrator settings. The default if not specified is 3600 seconds.",
              javaType = "Integer")
    String ASSUME_ROLE_DURATION_SECONDS = "CamelAwsStsAssumeRoleDurationSeconds";

    String ACCESS_KEY_ID = "CamelAwsStsAccessKeyId";
    String SECRET_KEY_ID = "CamelAwsStsSecretKey";
}
