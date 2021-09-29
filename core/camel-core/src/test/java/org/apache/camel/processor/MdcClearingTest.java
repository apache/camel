package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MdcClearingTest extends ContextTestSupport {

    public static final String CAMEL_BREADCRUMB_ID = "camel.breadcrumbId";
    public static final String BREADCRUMB_ID = "breadcrumbId";
    public static final String MY_BREADCRUMB = "my breadcrumb";

    private static final Logger log = LoggerFactory.getLogger(MdcClearingTest.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(16);


    @Test
    public void shouldPropagateAndClearMdcInSyncRoute() {
        // given
        MDC.remove(CAMEL_BREADCRUMB_ID);

        // when
        template.requestBodyAndHeader("direct:test-sync", "TEST", BREADCRUMB_ID, MY_BREADCRUMB);

        // then
        assertNull(MDC.get(CAMEL_BREADCRUMB_ID));
    }

    @Test
    public void shouldPropagateAndClearMdcInAsyncRoute() {
        // given
        MDC.remove(CAMEL_BREADCRUMB_ID);

        // when
        template.requestBodyAndHeader("direct:test-async", "TEST", BREADCRUMB_ID, MY_BREADCRUMB);

        // then
        assertNull(MDC.get(CAMEL_BREADCRUMB_ID));
    }

    @Test
    public void shouldPropagateAndClearMdcInMixedRoute() {
        // given
        MDC.remove(CAMEL_BREADCRUMB_ID);

        // when
        template.requestBodyAndHeader("direct:test-mixed", "TEST", BREADCRUMB_ID, MY_BREADCRUMB);

        // then
        assertNull(MDC.get(CAMEL_BREADCRUMB_ID));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setUseMDCLogging(true);
        camelContext.setUseBreadcrumb(true);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test-sync")
                        .process(new MySyncProcessor("STEP 1"));

                from("direct:test-async")
                        .process(new MySyncProcessor("STEP 1"));

                from("direct:test-mixed")
                        .process(new MyAsyncProcessor("STEP 1"))
                        .process(new MySyncProcessor("STEP 2"));
            }
        };
    }

    private static class MySyncProcessor implements Processor {
        private final String msg;

        public MySyncProcessor(String msg) {
            this.msg = msg;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            log.info(msg);
            assertEquals(MY_BREADCRUMB, MDC.get(CAMEL_BREADCRUMB_ID));
        }
    }

    private class MyAsyncProcessor extends AsyncProcessorSupport {

        private final String msg;

        public MyAsyncProcessor(String msg) {
            this.msg = msg;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            log.info(msg);
            assertEquals(MY_BREADCRUMB, MDC.get(CAMEL_BREADCRUMB_ID));

            executorService.execute(() -> callback.done(false));
            return false;
        }
    }
}
