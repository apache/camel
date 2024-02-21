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
package org.apache.camel.component.jgroups;

import org.apache.camel.spi.Metadata;

public final class JGroupsConstants {

    @Metadata(description = "Address (`org.jgroups.Address`) of the channel associated with the\n" +
                            "endpoint.",
              javaType = "org.jgroups.Address")
    public static final String HEADER_JGROUPS_CHANNEL_ADDRESS = "JGROUPS_CHANNEL_ADDRESS";
    @Metadata(description = "*Consumer*: The `org.jgroups.Address` instance extracted by\n" +
                            "`org.jgroups.Message`.getDest() method of the consumed message.\n" +
                            "*Producer*: The custom destination `org.jgroups.Address` of the message to be sent.",
              javaType = "org.jgroups.Address")
    public static final String HEADER_JGROUPS_DEST = "JGROUPS_DEST";
    @Metadata(description = "*Consumer* : The `org.jgroups.Address` instance extracted by\n" +
                            "`org.jgroups.Message`.getSrc() method of the consumed message. \n" +
                            "*Producer*: The custom source `org.jgroups.Address` of the message to be sent.",
              javaType = "org.jgroups.Address")
    public static final String HEADER_JGROUPS_SRC = "JGROUPS_SRC";
    @Metadata(description = "The original `org.jgroups.Message` instance from which the body of the\n" +
                            "consumed message has been extracted.",
              javaType = "org.jgroups.Message")
    public static final String HEADER_JGROUPS_ORIGINAL_MESSAGE = "JGROUPS_ORIGINAL_MESSAGE";

    private JGroupsConstants() {

    }
}
