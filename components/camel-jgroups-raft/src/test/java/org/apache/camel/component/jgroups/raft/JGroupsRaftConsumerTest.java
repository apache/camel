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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsRaftConsumerTest extends JGroupsRaftAbstractTest {
    private static final String CLUSTER_NAME = "JGroupsRaftConsumerTest";
    private static final String CONFIGURED_ENDPOINT_URI = "jgroups-raft:" + CLUSTER_NAME + "?raftId=A&channelProperties=raftABC.xml&enableRoleChangeEvents=true";
    private static final String CONFIGURED_ENDPOINT_URI2 = "jgroups-raft:" + CLUSTER_NAME + "?raftId=B&channelProperties=raftABC.xml&enableRoleChangeEvents=true";
    private static final String CONFIGURED_ENDPOINT_URI3 = "jgroups-raft:" + CLUSTER_NAME + "?raftId=C&channelProperties=raftABC.xml&enableRoleChangeEvents=true";

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftConsumerTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(CONFIGURED_ENDPOINT_URI).to("mock:out");
                from(CONFIGURED_ENDPOINT_URI2).to("mock:out2");
                from(CONFIGURED_ENDPOINT_URI3).to("mock:out3");
            }
        };
    }

    @Test
    public void shouldReceiveChangeRoleEvents() throws Exception {
        JGroupsRaftEndpoint endpoint = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint endpoint2 = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI2, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint endpoint3 = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI3, JGroupsRaftEndpoint.class);

        waitForLeader(5, endpoint.getResolvedRaftHandle(), endpoint2.getResolvedRaftHandle(), endpoint3.getResolvedRaftHandle());

        MockEndpoint mock = getMockEndpoint("mock:out");
        MockEndpoint mock2 = getMockEndpoint("mock:out2");
        MockEndpoint mock3 = getMockEndpoint("mock:out3");

        Exchange leaderEventExchange = null;

        for (Exchange exc : mock.getReceivedExchanges()) {
            LOG.info("mock" + exc.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.class));
            if (leaderEventExchange != null) {
                break;
            }
            if (JGroupsRaftEventType.LEADER.equals(exc.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.class))) {
                leaderEventExchange = exc;
            }
        }
        for (Exchange exc : mock2.getReceivedExchanges()) {
            LOG.info("mock2" + exc.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.class));
            if (leaderEventExchange != null) {
                break;
            }
            if (JGroupsRaftEventType.LEADER.equals(exc.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.class))) {
                leaderEventExchange = exc;
            }
        }
        for (Exchange exc : mock3.getReceivedExchanges()) {
            LOG.info("mock3" + exc.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.class));
            if (leaderEventExchange != null) {
                break;
            }
            if (JGroupsRaftEventType.LEADER.equals(exc.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_EVENT_TYPE, JGroupsRaftEventType.class))) {
                leaderEventExchange = exc;
            }
        }

        checkHeaders(leaderEventExchange);
    }
}
