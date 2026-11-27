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
package org.apache.camel.component.asterisk;

import org.apache.camel.spi.Metadata;

public final class AsteriskConstants {
    @Metadata(label = "consumer",
              description = "The simple class name of the asterisk-java ManagerEvent in the body, for example NewChannelEvent or"
                            + " HangupEvent. Set by the consumer for each event.",
              javaType = "java.lang.String", important = true)
    public static final String EVENT_NAME = "CamelAsteriskEventName";
    @Metadata(label = "producer",
              description = "The extension whose state the producer queries with the EXTENSION_STATE action, for example"
                            + " 100. The other actions do not use it.",
              javaType = "java.lang.String")
    public static final String EXTENSION = "CamelAsteriskExtension";
    @Metadata(label = "producer",
              description = "The dialplan context that contains the extension of the CamelAsteriskExtension header, for the"
                            + " EXTENSION_STATE action. The other actions do not use it.",
              javaType = "java.lang.String")
    public static final String CONTEXT = "CamelAsteriskContext";
    @Metadata(label = "producer",
              description = "The AMI action that the producer sends for this message. Replaces the action option.",
              javaType = "org.apache.camel.component.asterisk.AsteriskAction")
    public static final String ACTION = "CamelAsteriskAction";

    private AsteriskConstants() {
    }
}
