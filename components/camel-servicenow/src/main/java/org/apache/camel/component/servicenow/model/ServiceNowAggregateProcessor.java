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
package org.apache.camel.component.servicenow.model;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowProducerProcessor;
import org.apache.camel.util.ObjectHelper;

public class ServiceNowAggregateProcessor extends ServiceNowProducerProcessor<ServiceNowAggregate> {

    public static final ServiceNowProducerProcessor.Supplier SUPPLIER = new ServiceNowProducerProcessor.Supplier() {
        @Override
        public Processor get(ServiceNowEndpoint endpoint) throws Exception {
            return new ServiceNowAggregateProcessor(endpoint);
        }
    };

    public ServiceNowAggregateProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint, ServiceNowAggregate.class);
    }

    @Override
    protected void doProcess(Exchange exchange, Class<?> model, String action, String tableName, String sysId) throws Exception {
        if (ObjectHelper.equal(ServiceNowConstants.ACTION_RETRIEVE, action, true)) {
            retrieveStats(exchange.getIn(), model, tableName);
        } else {
            throw new IllegalArgumentException("Unknown action " + action);
        }
    }

    private void retrieveStats(Message in, Class<?> model, String tableName) throws Exception {
        setBody(
            in,
            model,
            client.retrieveStats(
                tableName,
                in.getHeader(ServiceNowConstants.SYSPARM_QUERY, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_AVG_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_COUNT, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_MIN_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_MAX_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_SUM_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_GROUP_BY, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_ORDER_BY, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_HAVING, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_DISPLAY_VALUE, config.getDisplayValue(), String.class)
            )
        );
    }
}
