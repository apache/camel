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
package org.apache.camel.component.aws.ddb;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DdbComponentTest extends CamelTestSupport {

    @BindToRegistry("amazonDDBClient")
    private AmazonDDBClientMock amazonDDBClient = new AmazonDDBClientMock();

    @Test
    public void whenTableExistsThenDoesntCreateItOnStart() throws Exception {
        assertNull(amazonDDBClient.createTableRequest);
    }


    @Test
    public void whenTableIsMissingThenCreateItOnStart() throws Exception {
        DefaultProducerTemplate.newInstance(context,
                "aws-ddb://creatibleTable?amazonDDBClient=#amazonDDBClient");
        assertEquals("creatibleTable", amazonDDBClient.createTableRequest.getTableName());
    }
    
    @Test
    public void createEndpointWithOnlySecretKeyConfiguration() throws Exception {
        DdbComponent component = context.getComponent("aws-ddb", DdbComponent.class);
        component.createEndpoint("aws-ddb://activeTable?secretKey=xxx");
    }
    
    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        DdbComponent component = context.getComponent("aws-ddb", DdbComponent.class);
        component.createEndpoint("aws-ddb://activeTable?amazonDDBClient=#amazonDDBClient");
    }


    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        DdbComponent component = context.getComponent("aws-ddb", DdbComponent.class);
        component.createEndpoint("aws-ddb://activeTable?accessKey=xxx&secretKey=yyy");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("aws-ddb://activeTable?amazonDDBClient=#amazonDDBClient");
            }
        };
    }
}