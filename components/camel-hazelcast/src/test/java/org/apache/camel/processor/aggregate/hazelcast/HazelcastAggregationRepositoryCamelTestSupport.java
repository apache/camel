package org.apache.camel.processor.aggregate.hazelcast;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author Alexander Lomov
 *         Date: 04.01.14
 *         Time: 3:00
 */
public class HazelcastAggregationRepositoryCamelTestSupport extends CamelTestSupport {
    private static HazelcastInstance hzOne = null;
    private static HazelcastInstance hzTwo = null;

    protected static void doInitializeHazelcastInstances() {
        hzOne = Hazelcast.newHazelcastInstance();
        hzTwo = Hazelcast.newHazelcastInstance();
    }

    protected static void doDestroyHazelcastInstances() {
        hzOne.getLifecycleService().shutdown();
        hzTwo.getLifecycleService().shutdown();
    }

    protected static HazelcastInstance getFirstInstance() {
        return hzOne;
    }

    protected static HazelcastInstance getSecondInstance() {
        return hzTwo;
    }

    @BeforeClass
    public static void setUpHazelcastCluster() {
        doInitializeHazelcastInstances();
    }

    @AfterClass
    public static void shutDownHazelcastCluster() {
        doDestroyHazelcastInstances();
    }
}
