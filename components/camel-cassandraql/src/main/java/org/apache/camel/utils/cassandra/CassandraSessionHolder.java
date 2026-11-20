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
package org.apache.camel.utils.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;

/**
 * Holds a Cassandra Session and manages its lifecycle
 */
public class CassandraSessionHolder {
    /**
     * Session
     */
    private CqlSession session;
    /**
     * Indicates whether Session is externally managed
     */
    private final boolean managedSession;

    /**
     * Holds a session that is managed outside: {@link #stop()} does not close it. Others (such as cql endpoints, or
     * other repositories) can use the same session, and the holder can be started again after a stop. Use
     * {@link #CassandraSessionHolder(CqlSession, boolean)} with {@code false} for a session that {@link #stop()}
     * closes.
     *
     * @param session the session
     */
    public CassandraSessionHolder(CqlSession session) {
        this(session, true);
    }

    /**
     * @param session        the session
     * @param managedSession whether the session is managed outside of the holder; if false, {@link #stop()} closes it
     */
    public CassandraSessionHolder(CqlSession session, boolean managedSession) {
        this.session = session;
        this.managedSession = managedSession;
    }

    public void start() {
    }

    public void stop() {
        // a second stop (such as after a start that failed) has nothing left to close
        if (!managedSession && session != null) {
            session.close();
            session = null;
        }
    }

    public CqlSession getSession() {
        return session;
    }
}
