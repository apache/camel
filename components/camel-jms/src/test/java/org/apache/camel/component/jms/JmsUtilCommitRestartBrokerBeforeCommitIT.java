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
package org.apache.camel.component.jms;

import java.io.IOException;
import java.net.URISyntaxException;

import jakarta.jms.JMSException;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;
import org.springframework.jms.support.JmsUtils;

/**
 * Test to prove that using org.springframework.jms.supportJmsUtils.commitIfNecessary if a broker restart or a network
 * issue happens after sending and before executing the commit, the message is not committed and the caller is not
 * notified, so the route continue with the happy path.
 *
 * https://issues.apache.org/jira/browse/CAMEL-20521
 */
public class JmsUtilCommitRestartBrokerBeforeCommitIT extends AbstractRestartBrokerBeforeCommitIT {

    @Test
    public void useJmsUtilCommitIfNecessaryTest() throws IOException, URISyntaxException, InterruptedException {
        int okMessageExpectedCount = 1;
        int jmsExceptionCount = 0;
        int exceptionMessageExpectedCount = 0;
        performTest(jmsExceptionCount, okMessageExpectedCount, exceptionMessageExpectedCount);
    }

    @Override
    protected void commit(Session session) throws JMSException {
        JmsUtils.commitIfNecessary(session);
    }
}
