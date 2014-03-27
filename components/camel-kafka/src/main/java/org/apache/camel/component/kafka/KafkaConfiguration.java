package org.apache.camel.component.kafka;

import kafka.producer.DefaultPartitioner;

public class KafkaConfiguration {
    private String zookeeperHost;
    private int zookeeperPort=2181;
    private String topic;
    private String groupId;
    private String partitioner = DefaultPartitioner.class.getCanonicalName();
    private int consumerStreams = 10;

    public KafkaConfiguration() {
    }

    public String getZookeeperHost() {
        return zookeeperHost;
    }

    public void setZookeeperHost(String zookeeperHost) {
        this.zookeeperHost = zookeeperHost;
    }

    public int getZookeeperPort() {
        return zookeeperPort;
    }

    public void setZookeeperPort(int zookeeperPort) {
        this.zookeeperPort = zookeeperPort;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getPartitioner() {
        return partitioner;
    }

    public void setPartitioner(String partitioner) {
        this.partitioner = partitioner;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getConsumerStreams() {
        return consumerStreams;
    }

    public void setConsumerStreams(int consumerStreams) {
        this.consumerStreams = consumerStreams;
    }
}