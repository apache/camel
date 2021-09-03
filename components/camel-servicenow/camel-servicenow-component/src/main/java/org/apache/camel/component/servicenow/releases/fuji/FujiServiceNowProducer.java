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
package org.apache.camel.component.servicenow.releases.fuji;

import org.apache.camel.Exchange;
import org.apache.camel.component.servicenow.AbstractServiceNowProducer;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowRelease;
import org.apache.camel.spi.InvokeOnHeader;

/**
 * The Fuji ServiceNow producer.
 */
public class FujiServiceNowProducer extends AbstractServiceNowProducer {

    private final FujiServiceNowTableProcessor processor1;
    private final FujiServiceNowAggregateProcessor processor2;
    private final FujiServiceNowImportSetProcessor processor3;

    public FujiServiceNowProducer(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint, ServiceNowRelease.FUJI);
        processor1 = new FujiServiceNowTableProcessor(endpoint);
        processor2 = new FujiServiceNowAggregateProcessor(endpoint);
        processor3 = new FujiServiceNowImportSetProcessor(endpoint);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_TABLE)
    public void invokeProcessor1(Exchange exchange) throws Exception {
        processor1.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_AGGREGATE)
    public void invokeProcessor2(Exchange exchange) throws Exception {
        processor2.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_IMPORT)
    public void invokeProcessor3(Exchange exchange) throws Exception {
        processor3.process(exchange);
    }
}
