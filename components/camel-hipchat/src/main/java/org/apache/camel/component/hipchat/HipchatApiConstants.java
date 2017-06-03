/**
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
package org.apache.camel.component.hipchat;

/**
 * List of constants specifically used for invoking Hipchat API
 */
interface HipchatApiConstants {
    String URI_PATH_ROOM_NOTIFY = "/v2/room/%s/notification";
    String URI_PATH_USER_MESSAGE = "/v2/user/%s/message";
    String URI_PATH_USER_LATEST_PRIVATE_CHAT = "/v2/user/%s/history/latest";
    String API_MESSAGE_COLOR = "color";
    String API_MESSAGE_FORMAT = "message_format";
    String API_MESSAGE = "message";
    String API_DATE = "date";
    String API_ITEMS = "items";
    String API_MESSAGE_NOTIFY = "notify";
    String AUTH_TOKEN_PREFIX = "?auth_token=";
    String DEFAULT_MAX_RESULT = "max-results=1";
}
