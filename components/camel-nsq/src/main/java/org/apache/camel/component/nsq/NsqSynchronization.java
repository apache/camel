package org.apache.camel.component.nsq;

import com.github.brainlag.nsq.NSQMessage;
import org.apache.camel.Exchange;
import org.apache.camel.support.SynchronizationAdapter;

public class NsqSynchronization extends SynchronizationAdapter {

    private final NSQMessage nsqMessage;
    private final int requeueInterval;

    public NsqSynchronization(NSQMessage nsqMessage, int requeueInterval) {
        this.nsqMessage = nsqMessage;
        this.requeueInterval = requeueInterval;
    }

    @Override
    public void onComplete(Exchange exchange) {
        nsqMessage.finished();
    }

    @Override
    public void onFailure(Exchange exchange) {
        nsqMessage.requeue(requeueInterval);
    }
}
