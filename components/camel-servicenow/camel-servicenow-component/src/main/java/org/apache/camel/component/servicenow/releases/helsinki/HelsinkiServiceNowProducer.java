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
package org.apache.camel.component.servicenow.releases.helsinki;

import org.apache.camel.Exchange;
import org.apache.camel.component.servicenow.AbstractServiceNowProducer;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowRelease;
import org.apache.camel.spi.InvokeOnHeader;

/**
 * The Helsinki ServiceNow producer.
 */
public class HelsinkiServiceNowProducer extends AbstractServiceNowProducer {

    private final HelsinkiServiceNowTableProcessor processor1;
    private final HelsinkiServiceNowAggregateProcessor processor2;
    private final HelsinkiServiceNowImportSetProcessor processor3;
    private final HelsinkiServiceNowAttachmentProcessor processor4;
    private final HelsinkiServiceNowScorecardProcessor processor5;
    private final HelsinkiServiceNowMiscProcessor processor6;
    private final HelsinkiServiceNowServiceCatalogProcessor processor7;
    private final HelsinkiServiceNowServiceCatalogItemsProcessor processor8;
    private final HelsinkiServiceNowServiceCatalogCartsProcessor processor9;
    private final HelsinkiServiceNowServiceCatalogCategoriesProcessor processor10;

    public HelsinkiServiceNowProducer(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint, ServiceNowRelease.HELSINKI);
        processor1 = new HelsinkiServiceNowTableProcessor(endpoint);
        processor2 = new HelsinkiServiceNowAggregateProcessor(endpoint);
        processor3 = new HelsinkiServiceNowImportSetProcessor(endpoint);
        processor4 = new HelsinkiServiceNowAttachmentProcessor(endpoint);
        processor5 = new HelsinkiServiceNowScorecardProcessor(endpoint);
        processor6 = new HelsinkiServiceNowMiscProcessor(endpoint);
        processor7 = new HelsinkiServiceNowServiceCatalogProcessor(endpoint);
        processor8 = new HelsinkiServiceNowServiceCatalogItemsProcessor(endpoint);
        processor9 = new HelsinkiServiceNowServiceCatalogCartsProcessor(endpoint);
        processor10 = new HelsinkiServiceNowServiceCatalogCategoriesProcessor(endpoint);
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

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_ATTACHMENT)
    public void invokeProcessor4(Exchange exchange) throws Exception {
        processor4.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_SCORECARDS)
    public void invokeProcessor5(Exchange exchange) throws Exception {
        processor5.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_MISC)
    public void invokeProcessor6(Exchange exchange) throws Exception {
        processor6.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_SERVICE_CATALOG)
    public void invokeProcessor7(Exchange exchange) throws Exception {
        processor7.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_SERVICE_CATALOG_ITEMS)
    public void invokeProcessor8(Exchange exchange) throws Exception {
        processor8.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_SERVICE_CATALOG_CARTS)
    public void invokeProcessor9(Exchange exchange) throws Exception {
        processor9.process(exchange);
    }

    @InvokeOnHeader(ServiceNowConstants.RESOURCE_SERVICE_CATALOG_CATEGORIES)
    public void invokeProcessor10(Exchange exchange) throws Exception {
        processor10.process(exchange);
    }

}
