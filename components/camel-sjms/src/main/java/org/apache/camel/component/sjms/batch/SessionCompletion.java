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
package org.apache.camel.component.sjms.batch;

import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SessionCompletion implements Synchronization {
    private static final Logger LOG = LoggerFactory.getLogger(SessionCompletion.class);

    private final Session session;

    // TODO: add more details in the commit/rollback eg such as message id

    SessionCompletion(Session session) {
        this.session = session;
    }

    @Override
    public void onComplete(Exchange exchange) {
        try {
            LOG.debug("Committing");
            session.commit();
        } catch (JMSException ex) {
            LOG.warn("Exception caught while committing JMS session", ex);
        }
    }

    @Override
    public void onFailure(Exchange exchange) {
        try {
            LOG.debug("Rolling back");
            session.rollback();
        } catch (JMSException ex) {
            LOG.warn("Exception caught while rolling back JMS session", ex);
        }
    }
}
