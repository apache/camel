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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.timestream.query.AmazonTimestreamQueryClientMock;
import org.apache.camel.component.aws2.timestream.write.AmazonTimestreamWriteClientMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Timestream2OperationValidationTest extends CamelTestSupport {

    private static final String NO_OPERATION
            = "Operation must be specified, either with the operation option or the CamelAwsTimestreamOperation header";

    @BindToRegistry("awsTimestreamWriteClient")
    AmazonTimestreamWriteClientMock writeClientMock = new AmazonTimestreamWriteClientMock();

    @BindToRegistry("awsTimestreamQueryClient")
    AmazonTimestreamQueryClientMock queryClientMock = new AmazonTimestreamQueryClientMock();

    @Test
    public void writeWithoutOperationFails() {
        assertThatThrownBy(() -> template.sendBody("direct:writeNoOperation", null))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(NO_OPERATION);
    }

    @Test
    public void queryWithoutOperationFails() {
        assertThatThrownBy(() -> template.sendBody("direct:queryNoOperation", null))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(NO_OPERATION);
    }

    @Test
    public void writeWithQueryOperationHeaderFails() {
        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:writeNoOperation", null, Timestream2Constants.OPERATION,
                Timestream2Operations.query))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("The operation query is not an operation of the write client: use clientType=query");
    }

    @Test
    public void queryWithWriteOperationHeaderFails() {
        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:queryNoOperation", null, Timestream2Constants.OPERATION,
                "describeDatabase"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "The operation describeDatabase is not an operation of the query client: use clientType=write");
    }

    @Test
    public void queryWithWriteOperationOptionFailsOnStart() throws Exception {
        Producer producer = context.getEndpoint(
                "aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=describeDatabase")
                .createProducer();

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "The operation describeDatabase is not an operation of the query client: use clientType=write");
    }

    @Test
    public void writeWithQueryOperationOptionFailsOnStart() throws Exception {
        Producer producer = context.getEndpoint(
                "aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient&operation=createScheduledQuery")
                .createProducer();

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "The operation createScheduledQuery is not an operation of the write client: use clientType=query");
    }

    @Test
    public void unknownClientTypeFails() {
        assertThatThrownBy(() -> context.getEndpoint(
                "aws2-timestream://read:test?awsTimestreamQueryClient=#awsTimestreamQueryClient&operation=query"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Unknown clientType read: use write or query");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:writeNoOperation")
                        .to("aws2-timestream://write:test?awsTimestreamWriteClient=#awsTimestreamWriteClient");
                from("direct:queryNoOperation")
                        .to("aws2-timestream://query:test?awsTimestreamQueryClient=#awsTimestreamQueryClient");
            }
        };
    }
}
