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
package org.apache.camel.processor.aggregator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A #class: aggregation strategy whose class does not exist fails with the built-in strategy that was likely meant
 * (from the bean metadata on the classpath), or with the built-in strategies to pick from.
 */
public class AggregationStrategyClassNotFoundHintTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testWrongPackageSaysDidYouMean() {
        String msg = startWithStrategy("#class:com.foo.UseLatestAggregationStrategy");

        assertTrue(msg.contains("com.foo.UseLatestAggregationStrategy"), msg);
        assertTrue(msg.contains("did you mean org.apache.camel.processor.aggregate.UseLatestAggregationStrategy"
                                + " (org.apache.camel.AggregationStrategy)?"),
                msg);
    }

    @Test
    public void testNoPackageSaysDidYouMean() {
        String msg = startWithStrategy("#class:GroupedBodyAggregationStrategy");

        assertTrue(msg.contains("did you mean org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy"
                                + " (org.apache.camel.AggregationStrategy)?"),
                msg);
    }

    @Test
    public void testUnknownClassListsBuiltInStrategies() {
        String msg = startWithStrategy("#class:com.foo.MyStrategy");

        assertFalse(msg.contains("did you mean"), msg);
        assertTrue(msg.contains("check the package name; a class from another library needs its dependency added"), msg);
        assertTrue(msg.contains("the built-in AggregationStrategy beans are"), msg);
        assertTrue(msg.contains("AggregationStrategy (org.apache.camel.processor.aggregate."), msg);
        // more than 6 built-in strategies on the classpath, so the rest are counted
        assertTrue(msg.contains(" more)"), msg);
    }

    @Test
    public void testSplitStrategyAlsoGetsTheHint() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").split(body()).aggregationStrategy("#class:com.foo.StringAggregationStrategy")
                            .to("mock:result");
                }
            });
            context.start();
        });
        String msg = messages(e);

        assertTrue(msg.contains("did you mean org.apache.camel.processor.aggregate.StringAggregationStrategy"), msg);
    }

    private String startWithStrategy(String ref) {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").aggregate(header("id")).aggregationStrategy(ref).completionSize(1)
                            .to("mock:result");
                }
            });
            context.start();
        });
        String msg = messages(e);
        assertTrue(msg.contains("No bean could be found in the registry for: " + ref), msg);
        return msg;
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        boolean noSuchBean = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            noSuchBean |= t instanceof NoSuchBeanException;
            sb.append(t.getMessage()).append('\n');
        }
        assertTrue(noSuchBean, sb.toString());
        return sb.toString();
    }
}
