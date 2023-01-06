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
package org.apache.camel.component.pubnub;

import org.apache.camel.spi.Metadata;

public final class PubNubConstants {
    @Metadata(label = "producer", description = "The operation to perform.", javaType = "String")
    public static final String OPERATION = "CamelPubNubOperation";
    @Metadata(description = "The Timestamp for the event.", javaType = "Long")
    public static final String TIMETOKEN = "CamelPubNubTimeToken";
    @Metadata(label = "consumer", description = "The channel for which the message belongs.", javaType = "String")
    public static final String CHANNEL = "CamelPubNubChannel";
    @Metadata(label = "producer", description = "UUID to be used as a device identifier.", javaType = "String")
    public static final String UUID = "CamelPubNubUUID";

    private PubNubConstants() {

    }
}
