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
package org.apache.camel.component.iota;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.iota.jota.error.InternalException;
import org.junit.Ignore;
import org.junit.Test;

public class IOTAProducerTest extends CamelTestSupport {

    private static final String SEED = "IHDEENZYITYVYSPKAURUZAQKGVJEREFDJMYTANNXXGPZ9GJWTEOJJ9IPMXOGZNQLSNMFDSQOTZAEETUEA";
    private static final String ADDRESS = "LXQHWNY9CQOHPNMKFJFIJHGEPAENAOVFRDIBF99PPHDTWJDCGHLYETXT9NPUVSNKT9XDTDYNJKJCPQMZCCOZVXMTXC";

    private static final String IOTA_NODE_URL = "https://nodes.thetangle.org:443";

    @Ignore
    @Test
    public void sendTransferTest() throws Exception {
        final String message = "ILOVEAPACHECAMEL";

        MockEndpoint mock = getMockEndpoint("mock:iota-send-message-response");
        mock.expectedMinimumMessageCount(1);

        try {
            template.sendBody("direct:iota-send-message", message);
        } catch (Exception e) {
            if (e.getCause() instanceof InternalException) {
                boolean flaky = e.getCause().getMessage().contains("Couldn't get a response from nodes");
                if (!flaky) {
                    throw e;
                } else {
                    log.warn("Flaky test as IOTA is not online and returning a response in time");
                    return;
                }
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void getNewAddressTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:iota-new-address-response");
        mock.expectedMinimumMessageCount(1);

        try {
            template.sendBody("direct:iota-new-address", "");
        } catch (Exception e) {
            if (e.getCause() instanceof InternalException) {
                boolean flaky = e.getCause().getMessage().contains("Couldn't get a response from nodes");
                if (!flaky) {
                    throw e;
                } else {
                    log.warn("Flaky test as IOTA is not online and returning a response in time");
                    return;
                }
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void getTransfersTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:iota-get-transfers-response");
        mock.expectedMinimumMessageCount(1);

        try {
            template.sendBody("direct:iota-get-transfers", "");
        } catch (Exception e) {
            if (e.getCause() instanceof InternalException) {
                boolean flaky = e.getCause().getMessage().contains("Couldn't get a response from nodes");
                if (!flaky) {
                    throw e;
                } else {
                    log.warn("Flaky test as IOTA is not online and returning a response in time");
                    return;
                }
            }
        }

        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:iota-send-message")
                    .setHeader(IOTAConstants.SEED_HEADER, constant(SEED))
                    .setHeader(IOTAConstants.TO_ADDRESS_HEADER, constant(ADDRESS))
                    .to("iota://test?url=" + IOTA_NODE_URL + "&securityLevel=2&tag=APACHECAMELTEST&depth=3&operation=" + IOTAConstants.SEND_TRANSFER_OPERATION)
                    .to("mock:iota-send-message-response");

                from("direct:iota-new-address")
                    .setHeader(IOTAConstants.SEED_HEADER, constant(SEED))
                    .setHeader(IOTAConstants.ADDRESS_INDEX_HEADER, constant(1))
                    .to("iota://test?url=" + IOTA_NODE_URL + "&securityLevel=1&operation=" + IOTAConstants.GET_NEW_ADDRESS_OPERATION)
                    .to("mock:iota-new-address-response");
                
                from("direct:iota-get-transfers")
                    .setHeader(IOTAConstants.SEED_HEADER, constant(SEED))
                    .setHeader(IOTAConstants.ADDRESS_START_INDEX_HEADER, constant(1))
                    .setHeader(IOTAConstants.ADDRESS_END_INDEX_HEADER, constant(10))
                    .to("iota://test?url=" + IOTA_NODE_URL + "&securityLevel=1&operation=" + IOTAConstants.GET_TRANSFERS_OPERATION)
                    .to("mock:iota-get-transfers-response");
            }
        };
    }
}
