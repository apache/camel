package org.apache.camel.spring.boot.issues;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = {SimpleOgnlTest.class})
public class SimpleOgnlTest {
	@EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;
 
    @Produce(uri = "direct:start")
    protected ProducerTemplate template;
    
	@Test
    public void testSimpleOgnlListExpression() throws Exception {
		List<String> list = new ArrayList<String>();
		list.add("one");
		list.add("two");
 
        resultEndpoint.expectedBodiesReceived(list.get(0));
 
        template.sendBody(list);
 
        resultEndpoint.assertIsSatisfied();
    }
	
	@Configuration
    public static class ContextConfig {
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                public void configure() {
                    from("direct:start").setBody(simple("${body[0]}")).to("mock:result");
                }
            };
        }
    }
}
