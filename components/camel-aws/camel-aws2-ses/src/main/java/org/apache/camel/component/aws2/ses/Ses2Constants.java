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
package org.apache.camel.component.aws2.ses;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS SES component
 */
public interface Ses2Constants {

    @Metadata(description = "The sender's email address.", javaType = "String")
    String FROM = "CamelAwsSesFrom";
    @Metadata(description = "The Amazon SES message ID.", javaType = "String")
    String MESSAGE_ID = "CamelAwsSesMessageId";
    @Metadata(description = "The reply-to email address(es) for the message. Use comma to separate multiple values.",
              javaType = "String")
    String REPLY_TO_ADDRESSES = "CamelAwsSesReplyToAddresses";
    @Metadata(description = "The email address to which bounce notifications are to be forwarded.", javaType = "String")
    String RETURN_PATH = "CamelAwsSesReturnPath";
    @Metadata(description = "The subject of the message.", javaType = "String")
    String SUBJECT = "CamelAwsSesSubject";
    @Metadata(description = "List of comma separated destination email address.", javaType = "String")
    String TO = "CamelAwsSesTo";
    @Metadata(description = "List of comma separated destination carbon copy (cc) email address.", javaType = "String")
    String CC = "CamelAwsSesCc";
    @Metadata(description = "List of comma separated destination blind carbon copy (bcc) email address.", javaType = "String")
    String BCC = "CamelAwsSesBcc";
    @Metadata(description = "The flag to show if email content is HTML.", javaType = "Boolean")
    String HTML_EMAIL = "CamelAwsSesHtmlEmail";
    @Metadata(description = "TThe configuration set to send.", javaType = "String")
    String CONFIGURATION_SET = "CamelAwsSesConfigurationSet";
}
