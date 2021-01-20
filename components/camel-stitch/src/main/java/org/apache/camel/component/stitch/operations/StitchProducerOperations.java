package org.apache.camel.component.stitch.operations;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;
import org.apache.camel.component.stitch.StitchConfiguration;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StitchProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(StitchProducerOperations.class);

    private final StitchClient client;
    private final StitchConfiguration configuration;

    public StitchProducerOperations(StitchClient client, StitchConfiguration configuration) {
        ObjectHelper.notNull(client, "client");
        ObjectHelper.notNull(configuration, "configuration");

        this.client = client;
        this.configuration = configuration;
    }

    public boolean sendEvents(final Message inMessage, final AsyncCallback callback) {

        return false;
    }
}
