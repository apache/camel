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

/**
 * Constants used in Camel AWS SES component
 */
public interface Ses2Constants {

    String FROM = "CamelAwsSesFrom";
    String MESSAGE_ID = "CamelAwsSesMessageId";
    String REPLY_TO_ADDRESSES = "CamelAwsSesReplyToAddresses";
    String RETURN_PATH = "CamelAwsSesReturnPath";
    String SUBJECT = "CamelAwsSesSubject";
    String TO = "CamelAwsSesTo";
    String HTML_EMAIL = "CamelAwsSesHtmlEmail";
}
