package org.apache.camel.test.spring;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@RunWith(CamelSpringRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration
@TestPropertySource(properties = "fixedBody=Camel")
public class CamelSpringRunnerTestPropertySourceTest {

    @Produce(uri = "direct:in")
    private ProducerTemplate start;

    @EndpointInject(uri = "mock:out")
    private MockEndpoint end;

    @Test
    public void readsFileAndInlinedPropertiesFromAnnotation() throws Exception {
        end.expectedBodiesReceived("Camel");

        start.sendBody("Aardvark");

        end.assertIsSatisfied();
    }
}
