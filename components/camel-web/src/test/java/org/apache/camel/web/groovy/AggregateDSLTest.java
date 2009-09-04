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

package org.apache.camel.web.groovy;

import org.junit.Ignore;
import org.junit.Test;

/**
 * a test case for aggregate DSL
 */
public class AggregateDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testAggregate() throws Exception {
        String dsl = "from(\"direct:start\").aggregate().header(\"cheese\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testAggregateCommon() throws Exception {
        String dsl = "from(\"direct:start\").aggregate(header(\"cheese\")).to(\"mock:result\")";
        String expected = "from(\"direct:start\").aggregate().header(\"cheese\").to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    @Test
    public void testAggregateGroupedExchange() throws Exception {
        String dsl = "from(\"direct:start\").aggregate().simple(\"id\").batchTimeout(500L).groupExchanges().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testAggregateTimeoutOnly() throws Exception {
        String dsl = "from(\"direct:start\").aggregate(header(\"id\")).batchTimeout(3000).batchSize(0).to(\"mock:result\")";
        String expected = "from(\"direct:start\").aggregate().header(\"id\").batchTimeout(3000).batchSize(0).to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    /**
     * a route involving a external exception class: CamelException
     */
    @Test
    public void testAggregateAndOnException() throws Exception {
        String dsl = "errorHandler(deadLetterChannel(\"mock:error\"));" + "onException(CamelException.class).maximumRedeliveries(2);"
                     + "from(\"direct:start\").aggregate(header(\"id\")).to(\"mock:result\")";
        String expected = "errorHandler(deadLetterChannel(\"mock://error\"));" + "onException(CamelException.class).maximumRedeliveries(2);"
                          + "from(\"direct:start\").aggregate().header(\"id\").to(\"mock:result\")";
        assertEquals(expected, render(dsl));
    }

    /**
     * a set of routes that use split and aggregate DSL
     */
    @Test
    public void fixmeTestAggregateTimerAndTracer() throws Exception {
        String dsl = "from(\"timer://kickoff?period=9999910000\").setHeader(\"id\").constant(\"foo\").setBody().constant(\"a b c\").split(body().tokenize(\" \")).to(\"seda:splitted\");"
                     + "from(\"seda:splitted\").aggregate(header(\"id\")).to(\"mock:result\")";
        String expected = "from(\"timer://kickoff?period=9999910000\").setHeader(\"id\").constant(\"foo\").setBody().constant(\"a b c\").split(body().tokenize(\" \")).to(\"seda:splitted\");\n"
                          + "from(\"seda:splitted\").aggregate().header(\"id\").to(\"mock:result\")";
        assertEquals(expected, renderRoutes(dsl));
    }
}
