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
package org.apache.camel.component.aws2.sns;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS SNS module
 */
public interface Sns2Constants {

    @Metadata(description = "The Amazon SNS message ID.", javaType = "String")
    String MESSAGE_ID = "CamelAwsSnsMessageId";
    @Metadata(description = "The Amazon SNS message subject. If not set, the subject from the\n" +
                            "`SnsConfiguration` is used.",
              javaType = "String")
    String SUBJECT = "CamelAwsSnsSubject";
    @Metadata(description = "The message structure to use such as json.", javaType = "String")
    String MESSAGE_STRUCTURE = "CamelAwsSnsMessageStructure";
    String MESSAGE_GROUP_ID_PROPERTY = "CamelAwsSnsMessageGroupId";
}
