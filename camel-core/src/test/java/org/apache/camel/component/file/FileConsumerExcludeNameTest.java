package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test that file consumer will exclude pre and postfixes
 */
public class FileConsumerExcludeNameTest extends ContextTestSupport {

    public void testExludePreAndPostfixes() throws Exception {
        deleteDirectory("./target/exclude");
        prepareFiles();
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceived("Report 1", "Report 2");
        mock.assertIsSatisfied();
    }

    private void prepareFiles() throws Exception {
        String url = "file://target/exclude/";
        template.sendBodyAndHeader(url, "Hello World", FileComponent.HEADER_FILE_NAME, "hello.xml");
        template.sendBodyAndHeader(url, "Report 1", FileComponent.HEADER_FILE_NAME, "report1.txt");
        template.sendBodyAndHeader(url, "Bye World", FileComponent.HEADER_FILE_NAME, "secret.txt");
        template.sendBodyAndHeader(url, "Report 2", FileComponent.HEADER_FILE_NAME, "report2.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/exclude/?excludedNamePrefix=secret&excludedNamePostfix=xml")
                    .to("mock:result");
            }
        };
    }

}
