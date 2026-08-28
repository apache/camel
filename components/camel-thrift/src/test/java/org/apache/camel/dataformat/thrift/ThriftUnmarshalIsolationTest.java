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
package org.apache.camel.dataformat.thrift;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.thrift.generated.Operation;
import org.apache.camel.dataformat.thrift.generated.Work;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A data format instance is shared by every exchange on the route, and Thrift's {@code TBase.read()} assigns only the
 * fields present in the incoming bytes. Two messages unmarshalled through the same data format must therefore not be
 * able to observe each other's fields - including through an optional field that the second message omits.
 */
class ThriftUnmarshalIsolationTest extends CamelTestSupport {

    @Test
    void anOmittedOptionalFieldDoesNotInheritThePreviousMessageValue() {
        Work withComment = new Work();
        withComment.num1 = 1;
        withComment.num2 = 2;
        withComment.op = Operation.ADD;
        withComment.comment = "first message";

        Work withoutComment = new Work();
        withoutComment.num1 = 3;
        withoutComment.num2 = 4;
        withoutComment.op = Operation.SUBTRACT;

        Object firstBytes = template.requestBody("direct:marshal", withComment);
        Object secondBytes = template.requestBody("direct:marshal", withoutComment);

        Work first = (Work) template.requestBody("direct:unmarshal", firstBytes);
        Work second = (Work) template.requestBody("direct:unmarshal", secondBytes);

        assertThat(first.getComment()).isEqualTo("first message");
        assertThat(second.getComment()).isNull();
        assertThat(second.getNum1()).isEqualTo(3);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void anOmittedOptionalFieldDoesNotInheritTheDefaultInstanceValue() {
        Work withoutComment = new Work();
        withoutComment.num1 = 3;
        withoutComment.num2 = 4;
        withoutComment.op = Operation.SUBTRACT;

        Object bytes = template.requestBody("direct:marshal", withoutComment);

        Work result = (Work) template.requestBody("direct:unmarshal-populated-default", bytes);

        assertThat(result.getComment()).isNull();
        assertThat(result.getNum1()).isEqualTo(3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ThriftDataFormat format = new ThriftDataFormat(new Work());
                Work populatedDefault = new Work();
                populatedDefault.comment = "default value";
                ThriftDataFormat populatedDefaultFormat = new ThriftDataFormat(populatedDefault);
                from("direct:marshal").marshal(format);
                from("direct:unmarshal").unmarshal(format);
                from("direct:unmarshal-populated-default").unmarshal(populatedDefaultFormat);
            }
        };
    }
}
