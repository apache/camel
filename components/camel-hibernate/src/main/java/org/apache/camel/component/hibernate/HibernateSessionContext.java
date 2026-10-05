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
package org.apache.camel.component.hibernate;

import org.apache.camel.SafeCopyProperty;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

final class HibernateSessionContext implements SafeCopyProperty {

    private final Session session;
    private final SessionFactory sessionFactory;
    private final String tenantIdentifier;
    private final Thread ownerThread;
    private volatile boolean active = true;

    HibernateSessionContext(Session session, SessionFactory sessionFactory, String tenantIdentifier) {
        this.session = session;
        this.sessionFactory = sessionFactory;
        this.tenantIdentifier = tenantIdentifier;
        this.ownerThread = Thread.currentThread();
    }

    Session getSession(SessionFactory expectedSessionFactory, String expectedTenantIdentifier) {
        if (!active
                || session == null
                || ownerThread != Thread.currentThread()
                || sessionFactory != expectedSessionFactory
                || !java.util.Objects.equals(tenantIdentifier, expectedTenantIdentifier)) {
            return null;
        }

        return session;
    }

    void invalidate() {
        active = false;
    }

    @Override
    public HibernateSessionContext safeCopy() {
        return new HibernateSessionContext(null, sessionFactory, tenantIdentifier);
    }
}
