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
package org.apache.camel.dataformat.tarfile;

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class TarFileSplitIteratorCorruptTest extends CamelTestSupport {

    @Test
    public void testTarFileUnmarshal() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT)
                .isInstanceOf(IllegalStateException.class);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                TarFileDataFormat zf = new TarFileDataFormat();
                zf.setUsingIterator(true);

                errorHandler(deadLetterChannel("mock:dead"));

                from("file://src/test/resources/data?delay=10&fileName=corrupt.tar&noop=true")
                        .unmarshal(zf)
                        .split(bodyAs(Iterator.class)).streaming()
                        .convertBodyTo(String.class)
                        .to("mock:end")
                        .end();
            }
        };
    }
}
