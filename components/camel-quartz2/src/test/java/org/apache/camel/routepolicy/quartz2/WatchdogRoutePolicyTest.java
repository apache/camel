package org.apache.camel.routepolicy.quartz2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class WatchdogRoutePolicyTest {

    private TestAppender appender;
    private Logger logger = Logger.getLogger(Watcher.class);
    private CamelContext camelContext;

    @Before
    public void setUp() throws Exception {
        appender = new TestAppender();
        appender.setThreshold(Level.WARN);
        logger.addAppender(appender);

        camelContext = new DefaultCamelContext();
        camelContext.setUseMDCLogging(true);
        camelContext.setUseBreadcrumb(true);
    }

    @After
    public void tearDown() {
        logger.removeAppender(appender);
    }

    @Test
    public void watchDogTest() throws Exception {
        camelContext.addRoutes(new TestRouteBuilder());
        camelContext.start();
        assertEquals(0, appender.getLog().size()); // Nothing seen yet
        camelContext.createProducerTemplate().sendBody("direct:start", "ping");
        Thread.sleep(100);
        camelContext.createProducerTemplate().sendBody("direct:start", "ping");
        Thread.sleep(1000);
        assertEquals(0, appender.getLog().size()); // Min and max still satisfied
        camelContext.createProducerTemplate().sendBody("direct:start", "ping");
        Thread.sleep(2000);
        assertTrue(appender.getLog().size() > 0); // Max exceeded
    }

    @Test
    public void watchDogInflightTest() throws Exception {
        camelContext.addRoutes(new TestDelayedRouteBuilder());
        camelContext.start();
        assertEquals(0, appender.getLog().size()); // Nothing seen yet
        camelContext.createProducerTemplate().sendBody("direct:start", "ping");
        Thread.sleep(3500);
        assertTrue(appender.getLog().size() > 0); // Route stuck warnings
    }

    private class TestRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start")
                .routeId("testRoute")
                .routePolicy(new WatchdogRoutePolicy().addWatcher(new WatcherDefinition("* * * * * ?").name("test").window(1, TimeUnit.MINUTES).min(1).max(2)))
                .to("mock:result");
        }
    }

    private class TestDelayedRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start")
                .routeId("delayRoute")
                .routePolicy(new WatchdogRoutePolicy(5).addWatcher(new WatcherDefinition("* * * * * ?").name("delay").window(2, TimeUnit.SECONDS).enabled(true)))
                .delayer(3000)
                .to("mock:result");
        }
    }

    private class TestAppender extends AppenderSkeleton {

        private final List<LoggingEvent> log = new ArrayList<>();

        @Override
        protected void append(LoggingEvent loggingEvent) {
            log.add(loggingEvent);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }

        public List<LoggingEvent> getLog() {
            return new ArrayList<>(log);
        }
    }
}
