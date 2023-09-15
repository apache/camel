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
package org.apache.camel.component.aws2.redshiftdata;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.redshiftdata.model.ListDatabasesResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RedshiftData2ProducerTest extends CamelTestSupport {

    @BindToRegistry("awsRedshiftDataClient")
    AmazonRedshiftDataClientMock clientMock = new AmazonRedshiftDataClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void redshiftDataListDatabasesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listDatabases", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.listDatabases);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListDatabasesResponse resultGet = (ListDatabasesResponse) exchange.getIn().getBody();
        List<String> resultList = new ArrayList<>();
        resultList.add("database1");
        resultList.add("database2");
        assertEquals(resultList, resultGet.databases());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listDatabases")
                        .to("aws2-redshiftdata://test?awsRedshiftDataClient=#awsRedshiftDataClient&operation=listDatabases")
                        .to("mock:result");
                from("direct:listDatabasesPojo")
                        .to("aws2-redshiftdata://test?awsRedshiftDataClient=#awsRedshiftDataClient&operation=listDatabases&pojoRequest=true")
                        .to("mock:result");
            }
        };
    }
}
