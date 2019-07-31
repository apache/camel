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
package org.apache.camel.component.iec60870;

import java.util.Objects;

public class ConnectionId {
    private final String host;

    private final int port;

    private final String connectionId;

    public ConnectionId(final String host, final int port, final String connectionId) {
        Objects.requireNonNull(host);

        if (port <= 0) {
            throw new IllegalArgumentException("Port must be greater than 0");
        }

        this.host = host;
        this.port = port;
        this.connectionId = connectionId;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public String getConnectionId() {
        return this.connectionId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.connectionId == null ? 0 : this.connectionId.hashCode());
        result = prime * result + (this.host == null ? 0 : this.host.hashCode());
        result = prime * result + this.port;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ConnectionId other = (ConnectionId)obj;
        if (this.connectionId == null) {
            if (other.connectionId != null) {
                return false;
            }
        } else if (!this.connectionId.equals(other.connectionId)) {
            return false;
        }
        if (this.host == null) {
            if (other.host != null) {
                return false;
            }
        } else if (!this.host.equals(other.host)) {
            return false;
        }
        if (this.port != other.port) {
            return false;
        }
        return true;
    }

}
