package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.strategy.FileProcessStrategySupport;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify that the noop file strategy usage of lock files.
 */
public class FileNoOpLockFileTest extends ContextTestSupport {

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        deleteDirectory("target/reports");
    }

    public void testLocked() throws Exception {
        deleteDirectory("target/reports");

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Locked");

        template.sendBodyAndHeader("file:target/reports/locked", "Hello Locked",
            FileComponent.HEADER_FILE_NAME, "report.txt");

        mock.assertIsSatisfied();

        // sleep to let file consumer do its unlocking
        Thread.sleep(200);

        // should be deleted after processing
        checkLockFile(false);
    }

    public void testNotLocked() throws Exception {
        deleteDirectory("target/reports");

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Not Locked");

        template.sendBodyAndHeader("file:target/reports/notlocked", "Hello Not Locked",
            FileComponent.HEADER_FILE_NAME, "report.txt");

        mock.assertIsSatisfied();

        // sleep to let file consumer do its unlocking
        Thread.sleep(200);

        // no lock files should exists after processing
        checkLockFile(false);
    }

    private static void checkLockFile(boolean expected) {
        String filename = "target/reports/";
        filename += expected ? "locked/" : "notlocked/";
        filename += "report.txt" + FileProcessStrategySupport.DEFAULT_LOCK_FILE_POSTFIX;

        File file = new File(filename);
        assertEquals("Lock file should " + (expected ? "exists" : "not exists"), expected, file.exists());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // for locks
                from("file://target/reports/locked/?noop=true").process(new MyNoopProcessor()).
                    to("mock:report");

                // for no locks
                from("file://target/reports/notlocked/?noop=true&lock=false").process(new MyNoopProcessor()).
                    to("mock:report");
            }
        };
    }

    private class MyNoopProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            boolean locked = "Hello Locked".equals(body);
            checkLockFile(locked);
        }
    }

}
