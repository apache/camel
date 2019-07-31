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

public final class JGroupsRaftConstants {
    public static final String HEADER_JGROUPSRAFT_LOG_SIZE = "JGROUPSRAFT_LOG_SIZE";
    public static final String DEFAULT_JGROUPSRAFT_CONFIG = "raft.xml";
    public static final String HEADER_JGROUPSRAFT_COMMIT_INDEX = "JGROUPSRAFT_COMMIT_INDEX";
    public static final String HEADER_JGROUPSRAFT_CURRENT_TERM = "JGROUPSRAFT_CURRENT_TERM";
    public static final String HEADER_JGROUPSRAFT_IS_LEADER = "JGROUPSRAFT_IS_LEADER";
    public static final String HEADER_JGROUPSRAFT_LAST_APPLIED = "JGROUPSRAFT_LAST_APPLIED";
    public static final String HEADER_JGROUPSRAFT_LEADER_ADDRESS = "JGROUPSRAFT_LEADER_ADDRESS";
    public static final String HEADER_JGROUPSRAFT_LOG_SIZE_BYTE = "JGROUPSRAFT_LOG_SIZE_BYTE";
    public static final String HEADER_JGROUPSRAFT_RAFT_ID = "JGROUPSRAFT_RAFT_ID";
    public static final String HEADER_JGROUPSRAFT_EVENT_TYPE = "JGROUPSRAFT_EVENT_TYPE";
    public static final String HEADER_JGROUPSRAFT_SET_OFFSET = "JGROUPSRAFT_SET_OFFSET";
    public static final String HEADER_JGROUPSRAFT_SET_LENGTH = "JGROUPSRAFT_SET_LENGTH";
    public static final String HEADER_JGROUPSRAFT_SET_TIMEOUT = "JGROUPSRAFT_SET_TIMEOUT";
    public static final String HEADER_JGROUPSRAFT_SET_TIMEUNIT = "JGROUPSRAFT_SET_TIMEUNIT";

    private JGroupsRaftConstants() {
        //utility class
    }
}
