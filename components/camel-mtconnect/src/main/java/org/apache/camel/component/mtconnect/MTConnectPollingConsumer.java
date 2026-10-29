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
package org.apache.camel.component.mtconnect;

import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;

/**
 * Reads one agent with repeated requests on Camel's scheduler, for agents behind a proxy or firewall that cuts
 * long-lived responses: each poll performs the next step of the {@link AgentProtocol}, a probe, a current or one sample
 * request from the cursor. The scheduler's delay is also the wait after a failure; the read timeout bounds the silence
 * of each answer.
 */
public class MTConnectPollingConsumer extends ScheduledPollConsumer {

    private final MTConnectEndpoint endpoint;
    private AgentReader reader;
    private AgentProtocol protocol;

    public MTConnectPollingConsumer(MTConnectEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        reader = AgentReader.open(endpoint, this);
        protocol = new AgentProtocol(endpoint, this, reader, false);
        // the scheduler starts last, once the protocol exists
        super.doStart();
    }

    @Override
    protected int poll() throws Exception {
        // a failed step is reported by the protocol; nothing for greedy to repeat at once
        AgentProtocol current = protocol;
        return current.step() ? current.delivered() : 0;
    }

    @Override
    protected void doStop() throws Exception {
        if (reader != null) {
            // a poll waiting for a silent agent returns at once
            reader.abort();
        }
        super.doStop();
        if (reader != null) {
            reader.close();
            reader = null;
        }
        protocol = null;
    }
}
