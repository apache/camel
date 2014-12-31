package org.apache.camel.builder.script;


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Ignore;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class RubyScriptTextTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    MockEndpoint resultEndpoint;

    final int messageCount = 500;

    @Test
    public void parallelExecutionWithCachedScriptAndReusedScriptEngine() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                getContext().getProperties().put(Exchange.REUSE_SCRIPT_ENGINE, "true");
                //getContext().getProperties().put(Exchange.COMPILE_SCRIPT, "true");

                from("seda:jruby?concurrentConsumers=5")
                    .to("language:ruby:puts $request.body;result = $request.body?cacheScript=true")
                    .to(resultEndpoint);
            }
        });

        resultEndpoint.setExpectedMessageCount(messageCount);
        resultEndpoint.assertNoDuplicates(body());

        for (int i = 1; i < messageCount + 1; i++) {
            template.sendBody("seda:jruby", "BODY" + i);
        }

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

    @Test
    @Ignore
    public void parallelExecutionWithoutCachedScriptAndNewScriptEngineForEachExchange() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                //getContext().getProperties().put(Exchange.COMPILE_SCRIPT, "true");

                from("seda:jruby?concurrentConsumers=5")
                    .to("language:ruby:puts $request.body;result = $request.body")
                    .to(resultEndpoint);
            }
        });

        resultEndpoint.setExpectedMessageCount(messageCount);
        resultEndpoint.assertNoDuplicates(body());

        for (int i = 1; i < messageCount + 1; i++) {
            template.sendBody("seda:jruby", "BODY" + i);
        }

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }
}
