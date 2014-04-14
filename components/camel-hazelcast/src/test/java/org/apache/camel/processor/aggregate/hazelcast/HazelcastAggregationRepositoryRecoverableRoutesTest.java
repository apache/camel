package org.apache.camel.processor.aggregate.hazelcast;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @author Alexander Lomov
 *         Date: 04.01.14
 *         Time: 4:37
 */

public class HazelcastAggregationRepositoryRecoverableRoutesTest extends HazelcastAggregationRepositoryCamelTestSupport {

    private static final String REPO_NAME = "routeTestRepo";
    private static final String MOCK_GOTCHA = "mock:gotcha";
    private static final String MOCK_FAILURE = "mock:failure";
    private static final String DIRECT_ONE = "direct:one";
    private static final String DIRECT_TWO = "direct:two";

    @EndpointInject(uri = MOCK_GOTCHA)
    private MockEndpoint mockGotcha;

    @EndpointInject(uri = MOCK_FAILURE)
    private MockEndpoint mockFailure;

    @Produce(uri = DIRECT_ONE)
    private ProducerTemplate produceOne;

    @Produce(uri = DIRECT_TWO)
    private ProducerTemplate produceTwo;

    @Test
    public void checkAggregationFromTwoRoutesWithRecovery() throws Exception {
        final HazelcastAggregationRepository repoOne =
                new HazelcastAggregationRepository(REPO_NAME, false, getFirstInstance());

        final HazelcastAggregationRepository repoTwo =
                new HazelcastAggregationRepository(REPO_NAME, false, getSecondInstance());

        final int completionSize = 4;
        final String correlator = "CORRELATOR";

        RouteBuilder rbOne = new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(EverythingIsLostException.class)
                    .handled(true)
                    .useOriginalMessage()
                    .to(MOCK_GOTCHA)
                .end();

                interceptSendToEndpoint(MOCK_FAILURE)
                    .throwException(new EverythingIsLostException("The field is lost... everything is lost"))
                .end();

                from(DIRECT_ONE)
                    .aggregate(header(correlator))
                    .aggregationRepository(repoOne)
                    .aggregationStrategy(new SumOfIntsAggregationStrategy())
                    .completionSize(completionSize)
                .to(MOCK_FAILURE);

            }
        };


        RouteBuilder rbTwo = new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(EverythingIsLostException.class)
                    .handled(true)
                    .useOriginalMessage()
                    .to(MOCK_GOTCHA)
                .end();

                interceptSendToEndpoint(MOCK_FAILURE)
                    .throwException(new EverythingIsLostException("The field is lost... everything is lost"))
                .end();

                from(DIRECT_TWO)
                    .aggregate(header(correlator))
                    .aggregationRepository(repoTwo)
                    .aggregationStrategy(new SumOfIntsAggregationStrategy())
                    .completionSize(completionSize)
                .to(MOCK_FAILURE);
            }
        };

        context().addRoutes(rbOne);
        context().addRoutes(rbTwo);
        context().start();

        mockFailure.expectedMessageCount(0);
        mockGotcha.expectedMessageCount(1);
        mockGotcha.expectedBodiesReceived(1 + 2 + 3 + 4);

        produceOne.sendBodyAndHeader(4, correlator, correlator);
        produceTwo.sendBodyAndHeader(2, correlator, correlator);
        produceOne.sendBodyAndHeader(3, correlator, correlator);
        produceTwo.sendBodyAndHeader(1, correlator, correlator);

        mockFailure.assertIsSatisfied();
        mockFailure.assertIsSatisfied();
    }

    @SuppressWarnings("unused")
    private static class EverythingIsLostException extends Exception {
        private EverythingIsLostException() {
        }

        private EverythingIsLostException(String message) {
            super(message);
        }

        private EverythingIsLostException(String message, Throwable cause) {
            super(message, cause);
        }

        private EverythingIsLostException(Throwable cause) {
            super(cause);
        }

        private EverythingIsLostException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
