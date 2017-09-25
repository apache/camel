package org.apache.camel.component.zookeeper.ha;

import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.impl.ha.ClusteredRoutePolicyFactory;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringZooKeeperClusteredRouteConfigurationTest extends CamelSpringTestSupport {

    @Test
    public void test() {
        assertNotNull(context.hasService(CamelClusterService.class));
        assertTrue(context.getRoutePolicyFactories().stream().anyMatch(ClusteredRoutePolicyFactory.class::isInstance));
    }

    // ***********************
    // Routes
    // ***********************

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/zookeeper/ha/SpringZooKeeperClusteredRouteConfigurationTest.xml");
    }
}
