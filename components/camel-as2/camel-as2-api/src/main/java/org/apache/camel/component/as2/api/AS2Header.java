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
package org.apache.camel.component.as2.api;

import org.apache.http.protocol.HTTP;

public interface AS2Header {

    /**
     * Message Header Name for MIME Version
     */
    String MIME_VERSION = "MIME-Version";
    /**
     * Message Header Name for AS2 From
     */
    String AS2_FROM = "AS2-From";
    /**
     * Message Header Name for AS2 Version
     */
    String AS2_VERSION = "AS2-Version";
    /**
     * Message Header Name for Content Type
     */
    String CONTENT_TYPE = "Content-Type";
    /**
     * Message Header Name for AS2 To
     */
    String AS2_TO = "AS2-To";
    /**
     * Message Header Name for From
     */
    String FROM = "From";
    /**
     * Message Header Name for Subject
     */
    String SUBJECT = "Subject";
    /**
     * Message Header Name for Message ID
     */
    String MESSAGE_ID = "Message-Id";
    /**
     * Message Header Name for Target Host
     */
    String TARGET_HOST = HTTP.TARGET_HOST;
    /**
     * Message Header Name for User Agent
     */
    String USER_AGENT = HTTP.USER_AGENT;
    /**
     * Message Header Name for Server Name
     */
    String SERVER = HTTP.SERVER_HEADER;
    /**
     * Message Header Name for Date
     */
    String DATE = HTTP.DATE_HEADER;
    /**
     * Message Header Name for Content Length
     */
    String CONTENT_LENGTH = HTTP.CONTENT_LEN;
    /**
     * Message Header Name for Connection
     */
    String CONNECTION = HTTP.CONN_DIRECTIVE;
    /**
     * Message Header Name for Expect
     */
    String EXPECT = HTTP.EXPECT_DIRECTIVE;
    /**
     * Message Header name for Content Transfer Encoding
     */
    String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    /**
     * Message Header name for Content Disposition
     */
    String CONTENT_DISPOSITION = "Content-Disposition";
    /**
     * Message Header name for Content Description
     */
    String CONTENT_DESCRIPTION = "Content-Description";
    /**
     * Message Header name for Disposition Type
     */
    String DISPOSITION_TYPE = "Disposition-Type";
    /**
     * Message Header name for Disposition Notification To
     */
    String DISPOSITION_NOTIFICATION_TO = "Disposition-Notification-To";
    /**
     * Message Header name for Receipt Delivery Option
     */
    String RECEIPT_DELIVERY_OPTION = "Receipt-Delivery-Option";
    /**
     * Message Header name for Receipt Address
     */
    String RECIPIENT_ADDRESS = "Recipient-Address";
    /**
     * Message Header name for Disposition Notification Options
     */
    String DISPOSITION_NOTIFICATION_OPTIONS = "Disposition-Notification-Options";

}
