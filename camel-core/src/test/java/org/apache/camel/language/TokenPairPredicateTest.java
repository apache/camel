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
package org.apache.camel.language;
import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class TokenPairPredicateTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/pair");
        super.setUp();
    }

    @Test
    public void testTokenPairPredicate() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/pair", "<hello>world!</hello>", Exchange.FILE_NAME, "hello.xml");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        File file = new File("target/pair/hello.xml");
        assertFalse("File should not exists " + file, file.exists());

        file = new File("target/pair/ok/hello.xml");
        assertTrue("File should exists " + file, file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/pair?initialDelay=0&delay=10&move=ok")
                    .choice()
                        // does not make so much sense to use a tokenPair in a predicate
                        // but you can do it nevertheless
                        .when().tokenizePair("<hello>", "</hello>").to("mock:result")
                    .end();
            }
        };
    }
}