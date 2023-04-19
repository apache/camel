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
package org.apache.camel.component.dhis2;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.dhis2.internal.Dhis2ApiName;
import org.apache.camel.component.dhis2.internal.Dhis2PropertiesHelper;
import org.apache.camel.support.component.AbstractApiProducer;
import org.apache.camel.support.component.ApiMethod;

public class Dhis2Producer extends AbstractApiProducer<Dhis2ApiName, Dhis2Configuration> {

    public Dhis2Producer(Dhis2Endpoint endpoint) {
        super(endpoint, Dhis2PropertiesHelper.getHelper(endpoint.getCamelContext()));
    }

    @Override
    protected ApiMethod findMethod(Exchange exchange, Map<String, Object> properties) {
        ApiMethod apiMethod = super.findMethod(exchange, properties);
        if (!properties.containsKey("resource")) {
            properties.put("resource", exchange.getIn().getBody());
        }
        return apiMethod;
    }

}
