package org.apache.camel.processor.aggregate.hazelcast;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.junit.Test;

/**
 * @author Alexander Lomov
 *         Date: 04.01.14
 *         Time: 2:40
 */

public class HazelcastAggregationRepositoryRoutesTest extends HazelcastAggregationRepositoryCamelTestSupport {

    private static final String REPO_NAME = "routeTestRepo";
    private static final String MOCK_GOTCHA = "mock:gotcha";
    private static final String DIRECT_ONE = "direct:one";
    private static final String DIRECT_TWO = "direct:two";

    @EndpointInject(uri = MOCK_GOTCHA)
    private MockEndpoint mock;

    @Produce(uri = DIRECT_ONE)
    private ProducerTemplate produceOne;

    @Produce(uri = DIRECT_TWO)
    private ProducerTemplate produceTwo;


    @Test
    public void checkAggregationFromTwoRoutes() throws Exception {
        final HazelcastAggregationRepository repoOne =
                new HazelcastAggregationRepository(REPO_NAME, false, getFirstInstance());

        final HazelcastAggregationRepository repoTwo =
                new HazelcastAggregationRepository(REPO_NAME, false, getSecondInstance());

        final int completionSize = 4;
        final String correlator = "CORRELATOR";
        RouteBuilder rbOne = new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from(DIRECT_ONE).routeId("AggregatingRouteOne")
                        .aggregate(header(correlator))
                        .aggregationRepository(repoOne)
                        .aggregationStrategy(new SumOfIntsAggregationStrategy())
                        .completionSize(completionSize)
                        .to(MOCK_GOTCHA);
            }
        };

        RouteBuilder rbTwo = new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from(DIRECT_TWO).routeId("AggregatingRouteTwo")
                        .aggregate(header(correlator))
                        .aggregationRepository(repoTwo)
                        .aggregationStrategy(new SumOfIntsAggregationStrategy())
                        .completionSize(completionSize)
                        .to(MOCK_GOTCHA);
            }
        };

        context().addRoutes(rbOne);
        context().addRoutes(rbTwo);
        context().start();

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(1 + 2 + 3 + 4);

        produceOne.sendBodyAndHeader(1, correlator, correlator);
        produceTwo.sendBodyAndHeader(2, correlator, correlator);
        produceOne.sendBodyAndHeader(3, correlator, correlator);
        produceOne.sendBodyAndHeader(4, correlator, correlator);

        mock.assertIsSatisfied();
    }

    private static class SumOfIntsAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            } else {
                Integer n = newExchange.getIn().getBody(Integer.class);
                Integer o = oldExchange.getIn().getBody(Integer.class);
                Integer v = (o == null ? 0 : o) + (n == null ? 0 : n);
                oldExchange.getIn().setBody(v, Integer.class);
                return oldExchange;
            }
        }
    }
}
