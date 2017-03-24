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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.hamcrest.core.Is;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonRestProcessorTest {

    @Test
    public void getRequestStream() throws Exception {
        SalesforceComponent comp = new SalesforceComponent();
        SalesforceEndpointConfig conf = new SalesforceEndpointConfig();
        OperationName op = OperationName.CREATE_BATCH;
        SalesforceEndpoint endpoint = new SalesforceEndpoint("", comp, conf, op, "");
        JsonRestProcessor jsonRestProcessor = new JsonRestProcessor(endpoint);
        DefaultCamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        TestObject doc = new TestObject();
        doc.setCreationDate(ZonedDateTime.of(1717, 1, 2, 3, 4, 5, 6, ZoneId.systemDefault()));

        exchange.getIn().setBody(doc);
        ByteArrayInputStream is = (ByteArrayInputStream) jsonRestProcessor.getRequestStream(exchange);
        String result = IOUtils.toString(is);
        assertThat(result, result.length() <= 48, Is.is(true));
    }

    static class TestObject extends AbstractDTOBase {

        private ZonedDateTime creationDate;

        public ZonedDateTime getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(ZonedDateTime creationDate) {
            this.creationDate = creationDate;
        }
    }

}