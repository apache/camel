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

    @Metadata(description = "The event ID of the cloud trail event consumed.",
              javaType = "String")
    String EVENT_ID = "CamelAwsCloudTrailEventId";
    @Metadata(description = "The event Name of the cloud trail event consumed.",
              javaType = "String")
    String EVENT_NAME = "CamelAwsCloudTrailEventName";
    @Metadata(description = "The event Source of the cloud trail event consumed.",
              javaType = "String")
    String EVENT_SOURCE = "CamelAwsCloudTrailEventSource";
    @Metadata(description = "The associated username of the event of the cloud trail event consumed.",
              javaType = "String")
    String USERNAME = "CamelAwsCloudTrailEventUsername";
}
