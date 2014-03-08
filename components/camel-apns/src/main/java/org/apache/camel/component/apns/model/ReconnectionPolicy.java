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
package org.apache.camel.component.apns.model;

public enum ReconnectionPolicy {

    /**
     * Only reconnect if absolutely needed, e.g. when the connection is dropped.
     * This is the recommended mode. Apple recommends using a persistent
     * connection. This improves the latency of sending push notification
     * messages. The down-side is that once the connection is closed
     * ungracefully (e.g. because Apple server drops it), the library wouldn't
     * detect such failure and not warn against the messages sent after the drop
     * before the detection.
     */
    NEVER,

    /**
     * Makes a new connection if the current connection has lasted for more than
     * half an hour. This is the sweat-spot in my experiments between dropped
     * connections while minimizing latency.
     */
    EVERY_HALF_HOUR,

    /**
     * Makes a new connection for every message being sent. This option ensures
     * that each message is actually delivered to Apple. If you send <strong>a
     * lot</strong> of messages though, Apple may consider your requests to be a
     * DoS attack.
     */
    EVERY_NOTIFICATION;
}
