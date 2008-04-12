package org.apache.camel.component.file.remote;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.ContextTestSupport;

/**
 * Unit test used for FTP wiki documentation
 */
public class FromFtpToBinarySampleTest extends ContextTestSupport {

    public void testDummy() throws Exception {
        // this is a noop test
    }

    // START SNIPPET: e1
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // we use a delay of 60 minutes (eg. once pr. hour we poll the FTP server
                long delay = 60 * 60 * 1000L;

                // from the given FTP server we poll (= download) all the files
                // from the public/reports folder as BINARY types and store this as files
                // in a local directory. Camle will use the filenames from the FTPServer

                // notice that the FTPConsumer properties must be prefixed with "consumer." in the URL
                // the delay parameter is from the FileConsumer component so we should use consumer.delay as
                // the URI parameter name. The FTP Component is an extension of the File Component.
                from("ftp://scott@localhost/public/reports?password=tiger&binary=true&consumer.delay=" + delay).
                    to("file://target/test-reports");
            }
        };
    }
    // END SNIPPET: e1

}
