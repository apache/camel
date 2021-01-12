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
package org.apache.camel.component.as2;

import org.apache.camel.Exchange;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.component.as2.internal.AS2PropertiesHelper;
import org.apache.camel.support.component.AbstractApiProducer;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpCoreContext;

/**
 * The AS2 producer.
 */
public class AS2Producer extends AbstractApiProducer<AS2ApiName, AS2Configuration> {

    public AS2Producer(AS2Endpoint endpoint) {
        super(endpoint, AS2PropertiesHelper.getHelper(endpoint.getCamelContext()));
    }

    @Override
    public void interceptResult(Object methodResult, Exchange resultExchange) {
        HttpCoreContext context = (HttpCoreContext) methodResult;
        resultExchange.setProperty(AS2Constants.AS2_INTERCHANGE, context);
        HttpResponse response = context.getResponse();
        HttpEntity entity = response.getEntity();
        if (entity instanceof DispositionNotificationMultipartReportEntity || entity instanceof MultipartSignedEntity) {
            resultExchange.getOut().setBody(entity);
        } else {
            resultExchange.getOut().setBody(null);
        }
    }
}
