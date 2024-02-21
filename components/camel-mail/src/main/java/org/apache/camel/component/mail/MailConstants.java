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
package org.apache.camel.component.mail;

import org.apache.camel.spi.Metadata;

public final class MailConstants {

    @Metadata(description = "Subject", javaType = "String")
    public static final String MAIL_SUBJECT = "Subject";
    @Metadata(description = "From", javaType = "String")
    public static final String MAIL_FROM = "From";
    @Metadata(description = "To", javaType = "String")
    public static final String MAIL_TO = "To";
    @Metadata(description = "Cc", javaType = "String")
    public static final String MAIL_CC = "Cc";
    @Metadata(description = "Bcc", javaType = "String")
    public static final String MAIL_BCC = "Bcc";
    @Metadata(description = "Reply to", javaType = "String")
    public static final String MAIL_REPLY_TO = "Reply-To";
    @Metadata(description = "The content type", javaType = "String")
    public static final String MAIL_CONTENT_TYPE = "contentType";
    @Metadata(label = "consumer",
              description = "After processing a mail message, it can be copied to a mail folder with the given name.",
              javaType = "String")
    public static final String MAIL_COPY_TO = "copyTo";
    @Metadata(label = "consumer",
              description = "After processing a mail message, it can be moved to a mail folder with the given name.",
              javaType = "String")
    public static final String MAIL_MOVE_TO = "moveTo";
    @Metadata(label = "consumer", description = "Deletes the messages after they have been processed.", javaType = "boolean")
    public static final String MAIL_DELETE = "delete";
    public static final String MAIL_ALTERNATIVE_BODY = "CamelMailAlternativeBody";
    public static final String MAIL_DEFAULT_FOLDER = "INBOX";
    public static final String MAIL_DEFAULT_FROM = "camel@localhost";
    @Metadata(description = "The message ID.", javaType = "String")
    public static final String MAIL_MESSAGE_ID = "CamelMailMessageId";
    public static final int MAIL_DEFAULT_CONNECTION_TIMEOUT = 30000;
    public static final String MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER = "never";
    public static final String MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_UUID = "uuid";
    public static final String MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER = "never";
    public static final String MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_UUID_PREFIX = "uuidPrefix";
    public static final String MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_UUID_SUFFIX = "uuidSuffix";

    private MailConstants() {
        // utility class
    }
}
