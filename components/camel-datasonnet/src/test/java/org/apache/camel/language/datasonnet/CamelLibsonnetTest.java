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
package org.apache.camel.language.datasonnet;

import com.datasonnet.document.MediaTypes;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CamelLibsonnetTest extends CamelTestSupport {

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // String helper tests
                from("direct:capitalize")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet'; c.capitalize('hello')",
                                String.class))
                        .to("mock:result");

                from("direct:stringOps")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " { trimmed: c.trim('  hi  '),"
                                              + "   parts: c.split('a,b,c', ','),"
                                              + "   joined: c.join(['x','y','z'], '-'),"
                                              + "   has: c.contains('hello world', 'world'),"
                                              + "   starts: c.startsWith('hello', 'hel'),"
                                              + "   ends: c.endsWith('hello', 'llo'),"
                                              + "   replaced: c.replace('foo bar foo', 'foo', 'baz'),"
                                              + "   low: c.lower('HELLO'),"
                                              + "   up: c.upper('hello')"
                                              + " }",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                // Collection helper tests
                from("direct:collectionOps")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " { total: c.sum([1, 2, 3, 4]),"
                                              + "   totalBy: c.sumBy([{v: 10}, {v: 20}], function(x) x.v),"
                                              + "   head: c.first([5, 6, 7]),"
                                              + "   tail: c.last([5, 6, 7]),"
                                              + "   len: c.count([1, 2, 3]),"
                                              + "   unique: c.distinct([1, 2, 2, 3, 3, 3]),"
                                              + "   smallest: c.min([3, 1, 2]),"
                                              + "   biggest: c.max([3, 1, 2]),"
                                              + "   taken: c.take([1,2,3,4,5], 3),"
                                              + "   dropped: c.drop([1,2,3,4,5], 2)"
                                              + " }",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                from("direct:firstEmpty")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet'; c.first([])",
                                String.class))
                        .to("mock:result");

                from("direct:groupBy")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " c.groupBy([{t:'a',v:1},{t:'b',v:2},{t:'a',v:3}], function(x) x.t)",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                from("direct:flatMap")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " c.flatMap([[1,2],[3,4]], function(x) x)",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                from("direct:sortBy")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " c.sortBy([{n:3},{n:1},{n:2}], function(x) x.n)",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                // Object helper tests
                from("direct:objectOps")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " { picked: c.pick({a:1, b:2, c:3}, ['a','c']),"
                                              + "   omitted: c.omit({a:1, b:2, c:3}, ['b']),"
                                              + "   merged: c.merge({a:1}, {b:2}),"
                                              + "   k: c.keys({x:1, y:2}),"
                                              + "   v: c.values({x:1, y:2})"
                                              + " }",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                from("direct:entries")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " c.entries({a:1, b:2})",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                from("direct:fromEntries")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " c.fromEntries([{key:'a',value:1},{key:'b',value:2}])",
                                String.class,
                                null, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");

                // Combined test with body data
                from("direct:transformWithLib")
                        .transform(datasonnet(
                                "local c = import 'camel.libsonnet';"
                                              + " { total: c.sumBy(body.items, function(i) i.price * i.qty),"
                                              + "   names: c.join(std.map(function(i) i.name, body.items), ', '),"
                                              + "   count: c.count(body.items)"
                                              + " }",
                                String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testCapitalize() throws Exception {
        assertEquals("Hello", sendAndGetString("direct:capitalize", ""));
    }

    @Test
    public void testStringOps() throws Exception {
        String result = sendAndGetString("direct:stringOps", "");
        JSONAssert.assertEquals(
                "{\"trimmed\":\"hi\","
                                + "\"parts\":[\"a\",\"b\",\"c\"],"
                                + "\"joined\":\"x-y-z\","
                                + "\"has\":true,"
                                + "\"starts\":true,"
                                + "\"ends\":true,"
                                + "\"replaced\":\"baz bar baz\","
                                + "\"low\":\"hello\","
                                + "\"up\":\"HELLO\"}",
                result, false);
    }

    @Test
    public void testCollectionOps() throws Exception {
        String result = sendAndGetString("direct:collectionOps", "");
        JSONAssert.assertEquals(
                "{\"total\":10,"
                                + "\"totalBy\":30,"
                                + "\"head\":5,"
                                + "\"tail\":7,"
                                + "\"len\":3,"
                                + "\"unique\":[1,2,3],"
                                + "\"smallest\":1,"
                                + "\"biggest\":3,"
                                + "\"taken\":[1,2,3],"
                                + "\"dropped\":[3,4,5]}",
                result, false);
    }

    @Test
    public void testFirstEmpty() throws Exception {
        Object result = sendAndGetBody("direct:firstEmpty", "");
        // null result from first([])
        assertEquals(null, result);
    }

    @Test
    public void testGroupBy() throws Exception {
        String result = sendAndGetString("direct:groupBy", "");
        JSONAssert.assertEquals(
                "{\"a\":[{\"t\":\"a\",\"v\":1},{\"t\":\"a\",\"v\":3}],\"b\":[{\"t\":\"b\",\"v\":2}]}",
                result, false);
    }

    @Test
    public void testFlatMap() throws Exception {
        String result = sendAndGetString("direct:flatMap", "");
        JSONAssert.assertEquals("[1,2,3,4]", result, false);
    }

    @Test
    public void testSortBy() throws Exception {
        String result = sendAndGetString("direct:sortBy", "");
        JSONAssert.assertEquals("[{\"n\":1},{\"n\":2},{\"n\":3}]", result, false);
    }

    @Test
    public void testObjectOps() throws Exception {
        String result = sendAndGetString("direct:objectOps", "");
        JSONAssert.assertEquals(
                "{\"picked\":{\"a\":1,\"c\":3},"
                                + "\"omitted\":{\"a\":1,\"c\":3},"
                                + "\"merged\":{\"a\":1,\"b\":2},"
                                + "\"k\":[\"x\",\"y\"],"
                                + "\"v\":[1,2]}",
                result, false);
    }

    @Test
    public void testEntries() throws Exception {
        String result = sendAndGetString("direct:entries", "");
        JSONAssert.assertEquals(
                "[{\"key\":\"a\",\"value\":1},{\"key\":\"b\",\"value\":2}]",
                result, false);
    }

    @Test
    public void testFromEntries() throws Exception {
        String result = sendAndGetString("direct:fromEntries", "");
        JSONAssert.assertEquals("{\"a\":1,\"b\":2}", result, false);
    }

    @Test
    public void testTransformWithLib() throws Exception {
        String payload
                = "{\"items\":[{\"name\":\"Widget\",\"price\":10,\"qty\":3},{\"name\":\"Gadget\",\"price\":25,\"qty\":2}]}";
        String result = sendAndGetString("direct:transformWithLib", payload);
        JSONAssert.assertEquals(
                "{\"total\":80,\"names\":\"Widget, Gadget\",\"count\":2}",
                result, false);
    }

    private String sendAndGetString(String uri, Object body) {
        template.sendBody(uri, body);
        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange exchange = mock.assertExchangeReceived(mock.getReceivedCounter() - 1);
        return exchange.getMessage().getBody(String.class);
    }

    private Object sendAndGetBody(String uri, Object body) {
        template.sendBody(uri, body);
        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange exchange = mock.assertExchangeReceived(mock.getReceivedCounter() - 1);
        return exchange.getMessage().getBody();
    }
}
