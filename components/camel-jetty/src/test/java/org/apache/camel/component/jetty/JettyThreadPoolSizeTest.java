package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class JettyThreadPoolSizeTest extends BaseJettyTest {


    private static final Logger log =  LoggerFactory.getLogger(JettyThreadPoolSizeTest.class);


    private JettyHttpComponent jettyComponent;

    private RouteBuilder builder;

    @Test
    public void threadPoolTest(){


        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        long initialJettyThreadNumber = threadSet.stream().filter(thread -> thread.getName().contains("CamelJettyServer")).count();

        log.info("initial Jetty thread number (expected 5): "+ initialJettyThreadNumber);

        context.stop();

        Set<Thread> threadSetAfterStop = Thread.getAllStackTraces().keySet();
        long jettyThreadNumberAfterStop = threadSetAfterStop.stream().filter(thread -> thread.getName().contains("CamelJettyServer")).count();

        log.info("Jetty thread number after stopping Camel Context: (expected 0): "+ jettyThreadNumberAfterStop);


        jettyComponent = (JettyHttpComponent)context.getComponent("jetty");
        jettyComponent.setMinThreads(5);
        jettyComponent.setMaxThreads(5);

        context.start();
        Set<Thread> threadSetAfterRestart = Thread.getAllStackTraces().keySet();
        long jettyThreadNumberAfterRestart = threadSetAfterRestart.stream().filter(thread -> thread.getName().contains("CamelJettyServer")).count();

        log.info("Jetty thread number after starting Camel Context: (expected 5): "+ jettyThreadNumberAfterRestart);


        assertEquals(5,initialJettyThreadNumber);

        assertEquals(0,jettyThreadNumberAfterStop);

        assertEquals(5,jettyThreadNumberAfterRestart);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup the jetty component with the custom minThreads
                jettyComponent = (JettyHttpComponent)context.getComponent("jetty");
                jettyComponent.setMinThreads(5);
                jettyComponent.setMaxThreads(5);

                from("jetty://http://localhost:{{port}}/myserverWithCustomPoolSize").to("mock:result");
            }
        };
        return builder;
    }

}
