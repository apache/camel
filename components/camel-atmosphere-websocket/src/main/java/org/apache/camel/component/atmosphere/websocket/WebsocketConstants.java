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
package org.apache.camel.component.atmosphere.websocket;

/**
 *
 */
public final class WebsocketConstants {
    
    public static final String CONNECTION_KEY = "websocket.connectionKey";
    public static final String CONNECTION_KEY_LIST = "websocket.connectionKey.list";
    public static final String SEND_TO_ALL = "websocket.sendToAll";
    public static final String EVENT_TYPE = "websocket.eventType";
    public static final String ERROR_TYPE = "websocket.errorType";

    public static final int ONOPEN_EVENT_TYPE = 1;
    public static final int ONCLOSE_EVENT_TYPE = 0;
    public static final int ONERROR_EVENT_TYPE = -1;

    public static final int MESSAGE_NOT_SENT_ERROR_TYPE = 1;

    private WebsocketConstants() {
        //helper class
    }
}
