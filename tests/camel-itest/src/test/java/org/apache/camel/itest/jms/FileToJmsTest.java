package org.apache.camel.itest.jms;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 *
 */
@ContextConfiguration
public class FileToJmsTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected ProducerTemplate template;
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    public void testFileToJms() throws Exception {
        result.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/jmsfile?append=false", "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");

        result.assertIsSatisfied();
    }

}
