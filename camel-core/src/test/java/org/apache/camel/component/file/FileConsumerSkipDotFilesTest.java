package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test that file consumer will skip any files starting with a dot
 */
public class FileConsumerSkipDotFilesTest extends ContextTestSupport {

    private String fileUrl = "file://target/dotfiles/";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/dotfiles");
    }

    public void testSkipDotFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("file:target/dotfiles/", "This is a dot file",
            FileComponent.HEADER_FILE_NAME, ".skipme");

        mock.setResultWaitTime(5000);
        mock.assertIsSatisfied();
    }

    public void testSkipDotFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/dotfiles/", "This is a dot file",
            FileComponent.HEADER_FILE_NAME, ".skipme");

        template.sendBodyAndHeader("file:target/dotfiles/", "Hello World",
            FileComponent.HEADER_FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).to("mock:result");
            }
        };
    }

}
