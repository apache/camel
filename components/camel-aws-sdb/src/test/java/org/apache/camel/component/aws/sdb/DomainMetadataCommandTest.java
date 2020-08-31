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
package org.apache.camel.component.aws.sdb;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DomainMetadataCommandTest {

    private DomainMetadataCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());

        command = new DomainMetadataCommand(sdbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        command.execute();

        assertEquals("DOMAIN1", sdbClient.domainMetadataRequest.getDomainName());

        assertEquals(Integer.valueOf(10), exchange.getIn().getHeader(SdbConstants.TIMESTAMP));
        assertEquals(Integer.valueOf(11), exchange.getIn().getHeader(SdbConstants.ITEM_COUNT));
        assertEquals(Integer.valueOf(12), exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_NAME_COUNT));
        assertEquals(Integer.valueOf(13), exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_VALUE_COUNT));
        assertEquals(Long.valueOf(1000000), exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_NAME_SIZE));
        assertEquals(Long.valueOf(2000000), exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_VALUE_SIZE));
        assertEquals(Long.valueOf(3000000), exchange.getIn().getHeader(SdbConstants.ITEM_NAME_SIZE));
    }
}
