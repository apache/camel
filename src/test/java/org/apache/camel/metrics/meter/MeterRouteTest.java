package org.apache.camel.metrics.meter;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.metrics.MetricsComponent;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = { MeterRouteTest.TestConfig.class },
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class MeterRouteTest {

    @EndpointInject(uri = "mock:out")
    private MockEndpoint endpoint;

    @Produce(uri = "direct:in")
    private ProducerTemplate producer;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:in")
                            .to("metrics:meter:A?mark=3179")
                            .to("mock:out");
                }
            };
        }

        @Bean(name = MetricsComponent.METRIC_REGISTRY_NAME)
        public MetricRegistry getMetricRegistry() {
            return Mockito.mock(MetricRegistry.class);
        }
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        // TODO - 12.05.2014, Lauri - is there any better way to set this up?
        MetricRegistry mockRegistry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(MetricsComponent.METRIC_REGISTRY_NAME, MetricRegistry.class);
        Meter mockMeter = Mockito.mock(Meter.class);
        InOrder inOrder = Mockito.inOrder(mockRegistry, mockMeter);
        when(mockRegistry.meter("B")).thenReturn(mockMeter);

        endpoint.expectedMessageCount(1);
        producer.sendBodyAndHeader(new Object(), MetricsComponent.HEADER_METRIC_NAME, "B");
        endpoint.assertIsSatisfied();
        inOrder.verify(mockRegistry, times(1)).meter("B");
        inOrder.verify(mockMeter, times(1)).mark(3179L);
        inOrder.verifyNoMoreInteractions();
    }
}
