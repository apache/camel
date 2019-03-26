package org.apache.camel.component.pulsar.utils.consumers;

import static org.mockito.Mockito.mock;

import org.apache.camel.component.pulsar.PulsarConsumer;
import org.junit.Test;
// TODO, write some tests
public class CommonCreationStrategyUtilsTest {

    @Test
    public void test() {
        PulsarConsumer pulsarConsumer = mock(PulsarConsumer.class);

        CommonCreationStrategyUtils.create("consumer_name", null , pulsarConsumer);
    }
}