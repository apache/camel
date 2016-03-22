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

public class ServiceNowTableProcessor extends ServiceNowProducerProcessor<ServiceNowTable> {

    public static final ServiceNowProducerProcessor.Supplier SUPPLIER = new ServiceNowProducerProcessor.Supplier() {
        @Override
        public Processor get(ServiceNowEndpoint endpoint) throws Exception {
            return new ServiceNowTableProcessor(endpoint);
        }
    };

    public ServiceNowTableProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint, ServiceNowTable.class);
    }

    @Override
    protected void doProcess(Exchange exchange, Class<?> model, String action, String tableName, String sysId) throws Exception {
        if (ObjectHelper.equal(ServiceNowConstants.ACTION_RETRIEVE, action, true)) {
            retrieveRecord(exchange.getIn(), model, tableName, sysId);
        } else if (ObjectHelper.equal(ServiceNowConstants.ACTION_CREATE, action, true)) {
            createRecord(exchange.getIn(), model, tableName);
        } else if (ObjectHelper.equal(ServiceNowConstants.ACTION_MODIFY, action, true)) {
            modifyRecord(exchange.getIn(), model, tableName, sysId);
        } else if (ObjectHelper.equal(ServiceNowConstants.ACTION_DELETE, action, true)) {
            deleteRecord(exchange.getIn(), model, tableName, sysId);
        } else if (ObjectHelper.equal(ServiceNowConstants.ACTION_UPDATE, action, true)) {
            updateRecord(exchange.getIn(), model, tableName, sysId);
        } else {
            throw new IllegalArgumentException("Unknown action " + action);
        }
    }

    /*
     * GET https://instance.service-now.com/api/now/table/{tableName}
     * GET https://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void retrieveRecord(Message in, Class<?> model, String tableName, String sysId) throws Exception {
        if (sysId == null) {
            setBody(
                in,
                model,
                client.retrieveRecord(
                    tableName,
                    in.getHeader(ServiceNowConstants.SYSPARM_QUERY, String.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_DISPLAY_VALUE, config.getDisplayValue(), String.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_EXCLUDE_REFERENCE_LINK, config.getExcludeReferenceLink(), Boolean.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_FIELDS, String.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_LIMIT, Integer.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_VIEW, String.class)
                )
            );
        } else {
            setBody(
                in,
                model,
                client.retrieveRecordById(
                    tableName,
                    ObjectHelper.notNull(sysId, "sysId"),
                    in.getHeader(ServiceNowConstants.SYSPARM_DISPLAY_VALUE, config.getDisplayValue(), String.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_EXCLUDE_REFERENCE_LINK, config.getExcludeReferenceLink(), Boolean.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_FIELDS, String.class),
                    in.getHeader(ServiceNowConstants.SYSPARM_VIEW, String.class)
                )
            );
        }
    }

    /*
     * POST https://instance.service-now.com/api/now/table/{tableName}
     */
    private void createRecord(Message in, Class<?> model, String tableName) throws Exception {
        validateBody(in, model);
        setBody(
            in,
            model,
            client.createRecord(
                tableName,
                in.getHeader(ServiceNowConstants.SYSPARM_DISPLAY_VALUE, config.getDisplayValue(), String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_EXCLUDE_REFERENCE_LINK, config.getExcludeReferenceLink(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_INPUT_DISPLAY_VALUE, config.getInputDisplayValue(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_SUPPRESS_AUTO_SYS_FIELD, config.getSuppressAutoSysField(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_VIEW, String.class),
                mapper.writeValueAsString(in.getBody())
            )
        );
    }

    /*
     * PUT https://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void modifyRecord(Message in, Class<?> model, String tableName, String sysId) throws Exception {
        validateBody(in, model);
        setBody(
            in,
            model,
            client.modifyRecord(
                tableName,
                ObjectHelper.notNull(sysId, "sysId"),
                in.getHeader(ServiceNowConstants.SYSPARM_DISPLAY_VALUE, config.getDisplayValue(), String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_EXCLUDE_REFERENCE_LINK, config.getExcludeReferenceLink(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_INPUT_DISPLAY_VALUE, config.getInputDisplayValue(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_SUPPRESS_AUTO_SYS_FIELD, config.getSuppressAutoSysField(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_VIEW, String.class),
                mapper.writeValueAsString(in.getBody())
            )
        );
    }

    /*
     * DELETE https://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void deleteRecord(Message in, Class<?> model, String tableName, String sysId) throws Exception {
        setBody(
            in,
            model,
            client.deleteRecord(
                tableName,
                ObjectHelper.notNull(sysId, "sysId"))
        );
    }

    /*
     * PATCH instance://dev21005.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void updateRecord(Message in, Class<?> model, String tableName, String sysId) throws Exception {
        validateBody(in, model);
        setBody(
            in,
            model,
            client.updateRecord(
                tableName,
                ObjectHelper.notNull(sysId, "sysId"),
                in.getHeader(ServiceNowConstants.SYSPARM_DISPLAY_VALUE, config.getDisplayValue(), String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_EXCLUDE_REFERENCE_LINK, config.getExcludeReferenceLink(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_FIELDS, String.class),
                in.getHeader(ServiceNowConstants.SYSPARM_INPUT_DISPLAY_VALUE, config.getInputDisplayValue(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_SUPPRESS_AUTO_SYS_FIELD, config.getSuppressAutoSysField(), Boolean.class),
                in.getHeader(ServiceNowConstants.SYSPARM_VIEW, String.class),
                mapper.writeValueAsString(in.getBody())
            )
        );
    }
}
