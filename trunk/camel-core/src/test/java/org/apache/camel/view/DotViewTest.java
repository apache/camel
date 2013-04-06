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
package org.apache.camel.view;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.component.bean.MyFooBean;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * @version 
 */
public class DotViewTest extends ContextTestSupport {
    protected String outputDirectory = "target/site/cameldoc";

    public void testGenerateFiles() throws Exception {
        RouteDotGenerator generator = new RouteDotGenerator(outputDirectory);
        generator.drawRoutes(context);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", new MyFooBean());
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        context.addRoutes(new MulticastRoute());
        context.addRoutes(new PipelineRoute());
        context.addRoutes(new AnotherPipelineRoute());
        context.addRoutes(new FromToRoute());
        context.addRoutes(new ChoiceRoute());
        context.addRoutes(new FilterRoute());
        context.addRoutes(new ComplexRoute());
        context.addRoutes(new FromToBeanRoute());
        context.addRoutes(new RoutingSlipRoute());
        context.addRoutes(new AggreagateRoute());
        context.addRoutes(new ResequenceRoute());
    }

    static class MulticastRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:multicast.in").
                    multicast().to("seda:multicast.out1", "seda:multicast.out2", "seda:multicast.out3");
        }
    }

    static class PipelineRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:pipeline.in").to("seda:pipeline.out1", "seda:pipeline.out2", "seda:pipeline.out3");
        }
    }

    static class AnotherPipelineRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:pipeline.in2").pipeline("seda:pipeline.out1", "seda:pipeline.out2", "seda:pipeline.out3");
        }
    }

    static class FromToRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:foo").to("seda:bar");
        }
    }

    static class FromToBeanRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:foo2").beanRef("myBean", "hello");
        }
    }

    static class RoutingSlipRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:foo3").routingSlip(header("splipHeader"));
        }
    }

    static class AggreagateRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:foo4")
                .aggregate(constant("messageId"), new UseLatestAggregationStrategy()).completionTimeout(1000L).
                    to("seda:aggregated");
        }
    }

    static class ResequenceRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("seda:foo5").resequence(constant("seqNum")).to("seda:bar");
        }
    }

    static class ChoiceRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("file:target/foo/xyz?noop=true").
                choice().
                  when(xpath("/person/city = 'London'")).to("file:target/messages/uk").
                  otherwise().to("file:target/messages/others");
        }
    }

    static class FilterRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("file:target/foo/bar?noop=true").filter(header("foo").isEqualTo("bar"))
                .to("file:target/xyz?noop=true");
        }
    }

    static class ComplexRoute extends RouteBuilder {
        public void configure() throws Exception {
            from("file:target/xyz?noop=true").filter(header("foo").isEqualTo("bar"))
                .recipientList(header("bar")).split(XPathBuilder.xpath("/invoice/lineItems")).throttle(3)
                .to("mock:result");
        }
    }
}