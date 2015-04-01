package org.apache.camel.rx;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.util.ServiceHelper;
import rx.Observable;
import rx.Subscriber;

public class CamelOperator implements Observable.Operator<Message, Message> {

    private Producer producer;

    public CamelOperator(CamelContext context, String uri) throws Exception {
        this(context.getEndpoint(uri));
    }

    public CamelOperator(Endpoint endpoint) throws Exception {
        this.producer = endpoint.createProducer();
        ServiceHelper.startService(producer);
    }

    @Override
    public Subscriber<? super Message> call(final Subscriber<? super Message> s) {
        return new Subscriber<Message>(s) {
            @Override
            public void onCompleted() {
                try {
                    ServiceHelper.stopService(producer);
                } catch (Exception e) {
                    throw new RuntimeCamelRxException(e);
                } finally {
                    producer = null;
                }
                if (!s.isUnsubscribed()) {
                    s.onCompleted();
                }
            }

            @Override
            public void onError(Throwable e) {
                Exchange exchange = producer.createExchange();
                exchange.setException(e);
                process(exchange);
                if (!s.isUnsubscribed()) {
                    s.onError(e);
                }
            }

            @Override
            public void onNext(Message item) {
                if (!s.isUnsubscribed()) {
                    s.onNext(process(item));
                }
            }
        };
    }

    private Exchange process(Exchange exchange) {
        try {
            producer.process(exchange);
            if (exchange.hasOut()) {
                exchange.setIn(exchange.getOut());
                exchange.setOut(null);
            }
        } catch (Exception e) {
            throw new RuntimeCamelRxException(e);
        }
        return exchange;
    }

    private Message process(Message message) {
        Exchange exchange = producer.createExchange();
        exchange.setIn(message);
        return process(exchange).getIn();
    }
}
