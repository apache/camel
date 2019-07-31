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
package org.apache.camel.dataformat.bindy.csv;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.csv.MyCsvRecord;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * The parsing issue when field ends with separator is fixed by updating BindyCvsDataFormat.unquoteTokens(..)<br>
 * See capture.png<br>
 * 
 * The suggested update does fix only the separator at the end of field.
 * !!! The separator in the beginning of the quoted field is still not handled.
 *
 */
public class BindyCsvFieldEndingWithSeparatorIssueTest extends CamelTestSupport {

    @Test
    public void testBindy() throws Exception {
        CamelContext ctx = new DefaultCamelContext();
        ctx.addRoutes(createRoute()); // new ReconciliationRoute()
        ctx.start();

        String addressLine1 = "8506 SIX FORKS ROAD,";

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedPropertyReceived("addressLine1", addressLine1);

        String csvLine = "\"PROBLEM SOLVER\",\"" + addressLine1
                         + "\",\"SUITE 104\",\"RALEIGH\",\"NC\",\"27615\",\"US\"";
        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:fromCsv", csvLine.trim());

        
        mock.assertIsSatisfied();

        // The algorithm of BindyCvsDataFormat.unquoteTokens(..) does not handle
        // separator at end of a field
        // addressLine1 results in the next field being appended -> '8506 SIX
        // FORKS ROAD,,SUITE 104'
    }

    @Test
    public void testBindyMoreSeparators() throws Exception {
        CamelContext ctx = new DefaultCamelContext();
        ctx.addRoutes(createRoute()); 
        ctx.start();

        String addressLine1 = "8506 SIX FORKS ROAD, , ,,, ,";

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedPropertyReceived("addressLine1", addressLine1);

        String csvLine = "\"PROBLEM SOLVER\",\"" + addressLine1
                         + "\",\"SUITE 104\",\"RALEIGH\",\"NC\",\"27615\",\"US\"";
        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:fromCsv", csvLine.trim());
        
        mock.assertIsSatisfied();

    }

    @Test
    @Ignore("This issue will be revisit when we have chance to rewrite bindy parser")
    public void testBindySeparatorsAround() throws Exception {
        CamelContext ctx = new DefaultCamelContext();
        ctx.addRoutes(createRoute()); // new ReconciliationRoute()
        ctx.start();

        // TODO The separator in the beginning of the quoted field is still not handled.
        // We may need to convert the separators in the quote into some kind of safe code 
        String addressLine1 = ",8506 SIX FORKS ROAD,";

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedPropertyReceived("addressLine1", addressLine1);

        String csvLine = "\"PROBLEM SOLVER\",\"" + addressLine1
                         + "\",\"SUITE 104\",\"RALEIGH\",\"NC\",\"27615\",\"US\"";
        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:fromCsv", csvLine.trim());
        
        mock.assertIsSatisfied();

    }

    private RouteBuilder createRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:fromCsv").unmarshal().bindy(BindyType.Csv, MyCsvRecord.class)
                    .setProperty("addressLine1", simple("${in.body.addressLine1}"))
                    .setProperty("addressLine2", simple("${in.body.addressLine2}")).log("${in.body}")
                    .to("mock:result");
            }
        };
    }

}
