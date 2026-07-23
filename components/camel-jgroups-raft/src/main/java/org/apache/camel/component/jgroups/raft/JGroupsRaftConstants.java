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
package org.apache.camel.component.jgroups.raft;

import org.apache.camel.spi.Metadata;

public final class JGroupsRaftConstants {
    @Metadata(label = "consumer", description = "The Raft log size in number of entries.", javaType = "int")
    public static final String HEADER_JGROUPSRAFT_LOG_SIZE = "CamelJGroupsRaftLogSize";
    public static final String DEFAULT_JGROUPSRAFT_CONFIG = "raft.xml";
    @Metadata(label = "consumer", description = "The commit index.", javaType = "int")
    public static final String HEADER_JGROUPSRAFT_COMMIT_INDEX = "CamelJGroupsRaftCommitIndex";
    @Metadata(label = "consumer", description = "The current raft term.", javaType = "int")
    public static final String HEADER_JGROUPSRAFT_CURRENT_TERM = "CamelJGroupsRaftCurrentTerm";
    @Metadata(label = "consumer", description = "Whether the node is the Raft Leader or not.", javaType = "boolean")
    public static final String HEADER_JGROUPSRAFT_IS_LEADER = "CamelJGroupsRaftIsLeader";
    @Metadata(label = "consumer", description = "The index of the last log entry that was appended to the log.",
              javaType = "int")
    public static final String HEADER_JGROUPSRAFT_LAST_APPLIED = "CamelJGroupsRaftLastApplied";
    @Metadata(label = "consumer", description = "The Address ot Raft Leader or not.", javaType = "org.jgroups.Address")
    public static final String HEADER_JGROUPSRAFT_LEADER_ADDRESS = "CamelJGroupsRaftLeaderAddress";
    @Metadata(label = "consumer", description = "The Raft id of the node.", javaType = "String")
    public static final String HEADER_JGROUPSRAFT_RAFT_ID = "CamelJGroupsRaftRaftId";
    @Metadata(label = "consumer", description = "The event type",
              javaType = "org.apache.camel.component.jgroups.raft.JGroupsRaftEventType")
    public static final String HEADER_JGROUPSRAFT_EVENT_TYPE = "CamelJGroupsRaftEventType";
    @Metadata(label = "producer", description = "Offset to use in the byte[] buffer to be set().", javaType = "Integer")
    public static final String HEADER_JGROUPSRAFT_SET_OFFSET = "CamelJGroupsRaftSetOffset";
    @Metadata(label = "producer", description = "Length to use in the byte[] buffer to be set().", javaType = "Integer")
    public static final String HEADER_JGROUPSRAFT_SET_LENGTH = "CamelJGroupsRaftSetLength";
    @Metadata(label = "producer", description = "Timeout to be used in set() operation.", javaType = "Long")
    public static final String HEADER_JGROUPSRAFT_SET_TIMEOUT = "CamelJGroupsRaftSetTimeout";
    @Metadata(label = "producer", description = "Timeunit to be used in set() operation.",
              javaType = "java.util.concurrent.TimeUnit")
    public static final String HEADER_JGROUPSRAFT_SET_TIMEUNIT = "CamelJGroupsRaftSetTimeUnit";

    private JGroupsRaftConstants() {
        //utility class
    }
}
