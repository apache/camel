package org.apache.camel.component.hazelcast;

import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class HazelcastComponentInstanceReferenceSpringTest extends HazelcastCamelSpringTestSupport {

    private static final String TEST_VALUE = "TestValue";
    private static final String TEST_KEY = "TestKey";


    @Test
    public void testComparePutAndGet() {
        template.sendBodyAndHeader("direct:testHazelcastInstanceBeanRefPut", TEST_VALUE,
                HazelcastConstants.OBJECT_ID, TEST_KEY);

        template.sendBodyAndHeader("direct:testHazelcastInstanceBeanRefGet", null,
                HazelcastConstants.OBJECT_ID, TEST_KEY);
        final Object testValueReturn = consumer.receiveBody("seda:out");

        assertEquals(TEST_VALUE, testValueReturn);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/META-INF/spring/test-camel-context-hazelcast-instance-reference.xml"
        );
    }
}

