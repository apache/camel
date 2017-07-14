/**
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

import org.apache.camel.component.servicenow.AbstractServiceNowProducer;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowRelease;

/**
 * The Helsinki ServiceNow producer.
 */
public class HelsinkiServiceNowProducer extends AbstractServiceNowProducer {
    public HelsinkiServiceNowProducer(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint, ServiceNowRelease.HELSINKI);

        bind(ServiceNowConstants.RESOURCE_TABLE, new HelsinkiServiceNowTableProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_AGGREGATE, new HelsinkiServiceNowAggregateProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_IMPORT, new HelsinkiServiceNowImportSetProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_ATTACHMENT, new HelsinkiServiceNowAttachmentProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_SCORECARDS, new HelsinkiServiceNowScorecardProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_MISC, new HelsinkiServiceNowMiscProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_SERVICE_CATALOG, new HelsinkiServiceNowServiceCatalogProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_SERVICE_CATALOG_ITEMS, new HelsinkiServiceNowServiceCatalogItemsProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_SERVICE_CATALOG_CARTS, new HelsinkiServiceNowServiceCatalogCartsProcessor(endpoint));
        bind(ServiceNowConstants.RESOURCE_SERVICE_CATALOG_CATEGORIES, new HelsinkiServiceNowServiceCatalogCategoriesProcessor(endpoint));
    }
}
