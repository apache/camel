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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Call setX methods on JGroups-raft cluster RaftHandle ({@code org.jgroups.raft.RaftHandle}).
 */
public class JGroupsRaftProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftProducer.class);

    // Producer settings
    private final JGroupsRaftEndpoint endpoint;

    // Constructor
    public JGroupsRaftProducer(JGroupsRaftEndpoint endpoint) {
        super(endpoint);

        this.endpoint = endpoint;
    }

    // Life cycle callbacks
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.connect();
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.disconnect();
        super.doStop();
    }

    // Processing logic
    @Override
    public void process(Exchange exchange) throws Exception {
        //TODO: implement possibility to call CompletableFuture<byte[]> setAsync(byte[] buf, int offset, int length);
        byte[] body = exchange.getIn().getBody(byte[].class);

        Integer setOffset = exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_SET_OFFSET, Integer.class);
        Integer setLength = exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_SET_LENGTH, Integer.class);
        Long setTimeout = exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_SET_TIMEOUT, Long.class);
        TimeUnit setTimeUnit = exchange.getIn().getHeader(JGroupsRaftConstants.HEADER_JGROUPSRAFT_SET_TIMEUNIT, TimeUnit.class);

        if (body != null) {
            byte[] result;
            if (setOffset != null && setLength != null && setTimeout != null && setTimeUnit != null) {
                LOG.debug("Calling set(byte[] {}, int {}, int {}, long {}, TimeUnit {}) method on raftHandle.", body, setOffset,
                        setLength, setTimeout, setTimeUnit);
                result = endpoint.getResolvedRaftHandle().set(body, setOffset, setLength, setTimeout, setTimeUnit);
            } else if (setOffset != null && setLength != null) {
                LOG.debug("Calling set(byte[] {}, int {}, int {}) method on raftHandle.", body, setOffset, setLength);
                result = endpoint.getResolvedRaftHandle().set(body, setOffset, setLength);
            } else {
                LOG.debug("Calling set(byte[] {}, int {}, int {} (i.e. body.length)) method on raftHandle.", body, 0,
                        body.length);
                result = endpoint.getResolvedRaftHandle().set(body, 0, body.length);
            }
            endpoint.populateJGroupsRaftHeaders(exchange);
            exchange.getIn().setBody(result);
        } else {
            LOG.debug("Body is null, cannot call set method on raftHandle.");
        }
    }

}
