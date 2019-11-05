package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class JettyThreadPoolSizeTest extends BaseJettyTest {


    private static final Logger LOG =  LoggerFactory.getLogger(JettyThreadPoolSizeTest.class);

    @Test
    public void threadPoolTest() {

        long initialJettyThreadNumber = countJettyThread();

        LOG.info("initial Jetty thread number (expected 5): " + initialJettyThreadNumber);

        context.stop();

        long jettyThreadNumberAfterStop =  countJettyThread();

        LOG.info("Jetty thread number after stopping Camel Context: (expected 0): " + jettyThreadNumberAfterStop);

        JettyHttpComponent jettyComponent = (JettyHttpComponent)context.getComponent("jetty");
        jettyComponent.setMinThreads(5);
        jettyComponent.setMaxThreads(5);

        context.start();

        long jettyThreadNumberAfterRestart = countJettyThread();

        LOG.info("Jetty thread number after starting Camel Context: (expected 5): "+ jettyThreadNumberAfterRestart);

        assertEquals(5L, initialJettyThreadNumber);

        assertEquals(0L, jettyThreadNumberAfterStop);

        assertEquals(5L, jettyThreadNumberAfterRestart);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new  RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup the jetty component with the custom minThreads
                JettyHttpComponent jettyComponent = (JettyHttpComponent)context.getComponent("jetty");
                jettyComponent.setMinThreads(5);
                jettyComponent.setMaxThreads(5);

                from("jetty://http://localhost:{{port}}/myserverWithCustomPoolSize").to("mock:result");
            }
        };
    }

    private long countJettyThread() {

        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        return threadSet.stream().filter(thread -> thread.getName().contains("CamelJettyServer")).count();
    }

}
