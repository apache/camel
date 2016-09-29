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

package org.apache.camel.component.servicenow.releases.fuji;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.servicenow.AbstractServiceNowProcessor;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowParams;
import org.apache.camel.util.ObjectHelper;

public abstract class FujiServiceNowProcessor extends AbstractServiceNowProcessor {
    protected FujiServiceNowProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = in.getHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), config.getTable(), String.class);
        final Class<?> model = getModel(in, tableName);
        final String action = in.getHeader(ServiceNowConstants.ACTION, String.class);
        final String sysId = in.getHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), String.class);

        doProcess(
            exchange,
            ObjectHelper.notNull(model, "model"),
            ObjectHelper.notNull(action, "action"),
            ObjectHelper.notNull(tableName, "tableName"),
            sysId);
    }

    protected abstract void doProcess(
        Exchange exchange,
        Class<?> model,
        String action,
        String tableName,
        String sysId) throws Exception;
}
