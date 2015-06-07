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

public interface HipchatConstants {

    String DEFAULT_HOST = "api.hipchat.com";
    int DEFAULT_PORT = 80;
    String DEFAULT_PROTOCOL = "http";
    String TO_USER = "HipchatToUser";
    String TO_USER_RESPONSE_STATUS = "HipchatToUserResponseStatus";
    String FROM_USER = "HipchatFromUser";
    String FROM_USER_RESPONSE_STATUS = "HipchatFromUserResponseStatus";
    String MESSAGE_DATE = "HipchatMessageDate";
    String TO_ROOM = "HipchatToRoom";
    String TO_ROOM_RESPONSE_STATUS = "HipchatToRoomResponseStatus";
    String TRIGGER_NOTIFY = "HipchatTriggerNotification";
    String MESSAGE_FORMAT = "HipchatMessageFormat";
    String MESSAGE_BACKGROUND_COLOR = "HipchatMessageBackgroundColor";
}
