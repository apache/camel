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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a photo to be sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputMediaPhoto extends InputMedia {

    private static final String TYPE = "photo";

    /**
     * Builds {@link InputMediaPhoto} instance.
     *
     * @param media     File to send. Pass a file_id to send a file that exists on the Telegram servers, or pass an
     *                  HTTP URL for Telegram to get a file from the Internet
     * @param caption   Optional. Caption of the video to be sent, 0-1024 characters
     * @param parseMode Optional. Send 'Markdown' or 'HTML', if you want Telegram apps to show bold, italic,
     *                  fixed-width text or inline URLs in the media caption.
     */
    public InputMediaPhoto(String media, String caption, String parseMode) {
        super(TYPE, media, caption, parseMode);
    }

    public InputMediaPhoto() {
        super.setType(TYPE);
    }
}
