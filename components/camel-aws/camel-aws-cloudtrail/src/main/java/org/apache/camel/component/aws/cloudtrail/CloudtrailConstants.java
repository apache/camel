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
package org.apache.camel.component.aws.cloudtrail;

import org.apache.camel.spi.Metadata;

public interface CloudtrailConstants {

    @Metadata(description = "The ID of the CloudTrail event. The consumer routes each event ID only once.",
              javaType = "String")
    String EVENT_ID = "CamelAwsCloudTrailEventId";
    @Metadata(description = "The name of the event, which is the API action that was called, for example"
                            + " PutBucketPolicy or ConsoleLogin.",
              javaType = "String")
    String EVENT_NAME = "CamelAwsCloudTrailEventName";
    @Metadata(description = "The event source, which is the AWS service that the request was made to, for"
                            + " example s3.amazonaws.com.",
              javaType = "String")
    String EVENT_SOURCE = "CamelAwsCloudTrailEventSource";
    @Metadata(description = "The user name or role name of the requester of the event. It is null when"
                            + " CloudTrail records none for the event.",
              javaType = "String")
    String USERNAME = "CamelAwsCloudTrailEventUsername";
}
