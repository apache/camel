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
package org.apache.camel.component.google.mail.stream;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel Google Mail Stream
 */
public final class GoogleMailStreamConstants {

    @Metadata(description = "The recipient of the message", javaType = "String")
    public static final String MAIL_TO = "CamelGoogleMailStreamTo";
    @Metadata(description = "The emitter of the message", javaType = "String")
    public static final String MAIL_FROM = "CamelGoogleMailStreamFrom";
    @Metadata(description = "The carbon copy of the message", javaType = "String")
    public static final String MAIL_CC = "CamelGoogleMailStreamCc";
    @Metadata(description = "The blind carbon cpoy of the message", javaType = "String")
    public static final String MAIL_BCC = "CamelGoogleMailStreamBcc";
    @Metadata(description = "The subject of the message", javaType = "String")
    public static final String MAIL_SUBJECT = "CamelGoogleMailStreamSubject";
    @Metadata(description = "The ID of the message", javaType = "String")
    public static final String MAIL_ID = "CamelGoogleMailId";

    /**
     * Prevent instantiation.
     */
    private GoogleMailStreamConstants() {
    }
}
