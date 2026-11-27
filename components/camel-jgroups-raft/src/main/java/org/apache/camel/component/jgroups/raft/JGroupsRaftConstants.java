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
    @Metadata(label = "consumer",
              description = "The number of entries in the Raft log of this member. The consumer sets it on each event,"
                            + " and the producer after each set.",
              javaType = "int")
    public static final String HEADER_JGROUPSRAFT_LOG_SIZE = "CamelJGroupsRaftLogSize";
    public static final String DEFAULT_JGROUPSRAFT_CONFIG = "raft.xml";
    @Metadata(label = "consumer",
              description = "The commit index: the index of the last log entry that is committed. The consumer sets it"
                            + " on each event, and the producer after each set.",
              javaType = "int")
    public static final String HEADER_JGROUPSRAFT_COMMIT_INDEX = "CamelJGroupsRaftCommitIndex";
    @Metadata(label = "consumer",
              description = "The current Raft term of this member. The consumer sets it on each event, and the producer"
                            + " after each set.",
              javaType = "int")
    public static final String HEADER_JGROUPSRAFT_CURRENT_TERM = "CamelJGroupsRaftCurrentTerm";
    @Metadata(label = "consumer",
              description = "Whether this member is the Raft leader. The consumer sets it on each event, and the"
                            + " producer after each set.",
              javaType = "boolean")
    public static final String HEADER_JGROUPSRAFT_IS_LEADER = "CamelJGroupsRaftIsLeader";
    @Metadata(label = "consumer",
              description = "The index of the last log entry that this member applied to its state machine. The"
                            + " consumer sets it on each event, and the producer after each set.",
              javaType = "int")
    public static final String HEADER_JGROUPSRAFT_LAST_APPLIED = "CamelJGroupsRaftLastApplied";
    @Metadata(label = "consumer",
              description = "The JGroups address of the Raft leader, or null if no leader is known. The consumer sets"
                            + " it on each event, and the producer after each set.",
              javaType = "org.jgroups.Address")
    public static final String HEADER_JGROUPSRAFT_LEADER_ADDRESS = "CamelJGroupsRaftLeaderAddress";
    @Metadata(label = "consumer",
              description = "The Raft ID of this member. The consumer sets it on each event, and the producer after"
                            + " each set.",
              javaType = "String")
    public static final String HEADER_JGROUPSRAFT_RAFT_ID = "CamelJGroupsRaftRaftId";
    @Metadata(label = "consumer",
              description = "The role that this member changed to: LEADER, or FOLLOWER (also for a Learner). The"
                            + " consumer sets it only with enableRoleChangeEvents.",
              javaType = "org.apache.camel.component.jgroups.raft.JGroupsRaftEventType")
    public static final String HEADER_JGROUPSRAFT_EVENT_TYPE = "CamelJGroupsRaftEventType";
    @Metadata(label = "producer",
              description = "The offset in the body byte array of the data to set. Used only together with"
                            + " CamelJGroupsRaftSetLength: if either is missing, the whole body is set.",
              javaType = "Integer")
    public static final String HEADER_JGROUPSRAFT_SET_OFFSET = "CamelJGroupsRaftSetOffset";
    @Metadata(label = "producer",
              description = "The number of bytes of the body to set, from CamelJGroupsRaftSetOffset. Used only together"
                            + " with CamelJGroupsRaftSetOffset: if either is missing, the whole body is set.",
              javaType = "Integer")
    public static final String HEADER_JGROUPSRAFT_SET_LENGTH = "CamelJGroupsRaftSetLength";
    @Metadata(label = "producer",
              description = "How long the producer waits for the set to complete, in the unit of"
                            + " CamelJGroupsRaftSetTimeUnit, before it fails with a TimeoutException. Used only when"
                            + " CamelJGroupsRaftSetOffset, CamelJGroupsRaftSetLength and CamelJGroupsRaftSetTimeUnit are"
                            + " set too; otherwise the producer waits without a timeout.",
              javaType = "Long")
    public static final String HEADER_JGROUPSRAFT_SET_TIMEOUT = "CamelJGroupsRaftSetTimeout";
    @Metadata(label = "producer",
              description = "The java.util.concurrent.TimeUnit of CamelJGroupsRaftSetTimeout. Used only when"
                            + " CamelJGroupsRaftSetOffset, CamelJGroupsRaftSetLength and CamelJGroupsRaftSetTimeout are"
                            + " set too.",
              javaType = "java.util.concurrent.TimeUnit")
    public static final String HEADER_JGROUPSRAFT_SET_TIMEUNIT = "CamelJGroupsRaftSetTimeUnit";

    private JGroupsRaftConstants() {
        //utility class
    }
}
