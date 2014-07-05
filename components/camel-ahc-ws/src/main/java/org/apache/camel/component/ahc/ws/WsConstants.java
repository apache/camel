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
package org.apache.camel.component.ahc.ws;

/**
 *
 */
public final class WsConstants {

    public static final String CONTENT_TYPE_JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";
    public static final String CONTENT_TYPE_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

    public static final String WS_PROTOCOL = "ahc-ws";
    public static final String WSS_PROTOCOL = "ahc-wss";

    private WsConstants() {
    }

}
