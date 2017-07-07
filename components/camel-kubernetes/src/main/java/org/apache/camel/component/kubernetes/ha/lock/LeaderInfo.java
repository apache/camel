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
package org.apache.camel.component.kubernetes.ha.lock;

import java.util.Date;

import org.apache.camel.util.ObjectHelper;

/**
 * Overview of a leadership status.
 */
public class LeaderInfo {

    private String groupName;

    private String leader;

    private Date timestamp;

    public LeaderInfo() {
    }

    public LeaderInfo(String groupName, String leader, Date timestamp) {
        this.groupName = groupName;
        this.leader = leader;
        this.timestamp = timestamp;
    }

    public boolean isTimeElapsedSeconds(long timeSeconds) {
        if (timestamp == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        return timestamp.getTime() + timeSeconds * 1000 <= now;
    }

    public boolean isLeader(String pod) {
        ObjectHelper.notNull(pod, "pod");
        return pod.equals(leader);
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LeaderInfo{");
        sb.append("groupName='").append(groupName).append('\'');
        sb.append(", leader='").append(leader).append('\'');
        sb.append(", timestamp=").append(timestamp);
        sb.append('}');
        return sb.toString();
    }

}
