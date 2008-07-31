package org.apache.camel.component.file.remote;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test that ftp consumer will exclude pre and postfixes 
 */
public class FtpConsumerExcludeNameTest extends FtpServerTestSupport {

    private String port = "20095";

    private String ftpUrl = "ftp://admin@localhost:" + port + "/excludename?password=admin"
        + "&consumer.excludedNamePrefix=secret&consumer.excludedNamePostfix=xml";

    public void testExludePreAndPostfixes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceived("Report 1", "Report 2");
        mock.assertIsSatisfied();
    }

    public String getPort() {
        return port;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        // test that we can pool and store as a local file
        String ftpUrl = "ftp://admin@localhost:" + port + "/excludename/?password=admin";
        template.sendBodyAndHeader(ftpUrl, "Hello World", FileComponent.HEADER_FILE_NAME, "hello.xml");
        template.sendBodyAndHeader(ftpUrl, "Report 1", FileComponent.HEADER_FILE_NAME, "report1.txt");
        template.sendBodyAndHeader(ftpUrl, "Bye World", FileComponent.HEADER_FILE_NAME, "secret.txt");
        template.sendBodyAndHeader(ftpUrl, "Report 2", FileComponent.HEADER_FILE_NAME, "report2.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(ftpUrl).to("mock:result");
            }
        };
    }

}