package org.apache.camel.component.kafka;

import kafka.producer.Partitioner;
import kafka.utils.VerifiableProperties;

/**
 * @author Stephen Samuel
 */
public class SimplePartitioner implements Partitioner<String> {

    public SimplePartitioner(VerifiableProperties props) {
    }

    /**
     * Uses the key to calculate a partition bucket id for routing
     * the data to the appropriate broker partition
     *
     * @return an integer between 0 and numPartitions-1
     */
    @Override
    public int partition(String key, int numPartitions) {
        return key.hashCode() % numPartitions;
    }
}
