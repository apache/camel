package org.apache.camel.component.aws2.kinesis.client;

import software.amazon.awssdk.services.kinesis.KinesisClient;

/**
 * Manage the required actions of a Kinesis client for either local or remote.
 */
public interface KinesisInternalClient {

    /**
     * Returns a Kinesis client after a factory method determines which one to return.
     *
     * @return KinesisClient client
     */
    KinesisClient getKinesisClient();
}
