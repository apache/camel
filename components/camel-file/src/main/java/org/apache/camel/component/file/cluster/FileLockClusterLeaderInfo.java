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
package org.apache.camel.component.file.cluster;

import java.util.Objects;

/**
 * Holds information about a file lock cluster leader.
 */
final class FileLockClusterLeaderInfo {
    private final String id;
    private final long heartbeatUpdateIntervalMilliseconds;
    private final long heartbeatMilliseconds;

    /**
     * Constructs a {@link FileLockClusterLeaderInfo}.
     *
     * @param id                                  The unique UUID assigned to the cluster leader
     * @param heartbeatUpdateIntervalMilliseconds The cluster leader heartbeat update interval value in milliseconds
     * @param heartbeatMilliseconds               The cluster leader heartbeat value in milliseconds
     */
    FileLockClusterLeaderInfo(String id, long heartbeatUpdateIntervalMilliseconds, long heartbeatMilliseconds) {
        Objects.requireNonNull(id);
        this.id = id;
        this.heartbeatUpdateIntervalMilliseconds = heartbeatUpdateIntervalMilliseconds;
        this.heartbeatMilliseconds = heartbeatMilliseconds;
    }

    String getId() {
        return id;
    }

    long getHeartbeatMilliseconds() {
        return heartbeatMilliseconds;
    }

    long getHeartbeatUpdateIntervalMilliseconds() {
        return heartbeatUpdateIntervalMilliseconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileLockClusterLeaderInfo that = (FileLockClusterLeaderInfo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
