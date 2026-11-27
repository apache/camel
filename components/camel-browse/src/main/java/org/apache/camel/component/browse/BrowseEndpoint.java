/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.browse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;

/**
 * Keep the messages sent to the endpoint in memory, so that tests, tools and the developer console can browse them.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "browse", title = "Browse", syntax = "browse:name",
             remote = false, internal = true, category = { Category.CORE, Category.MONITORING })
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "The body the producer sent: the route runs on the same exchange, in the"
                                                 + " thread of the producer"),
           producerIn = @BodyType(Carrier.ANY),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "Unchanged, unless a route consumes from the same browse endpoint: it runs on"
                                                 + " the same exchange, so the body is the one that route ends with"))
public class BrowseEndpoint extends DefaultEndpoint implements BrowsableEndpoint {

    @UriPath(description = "The name of the endpoint, which can be any string. The routes that send to and consume from"
                           + " the same endpoint URI share its list of messages.")
    @Metadata(required = true)
    private String name;
    @UriParam(label = "advanced",
              description = "The maximum number of messages that the endpoint keeps in memory for browsing. When a new"
                            + " message goes over the limit, the oldest message is removed from the list. The default is"
                            + " the browseLimit of the component, and 0 means no limit, so every message is kept until"
                            + " CamelContext stops.")
    private int browseLimit;
    @UriParam(label = "advanced",
              description = "A predicate that the producer tests on each message: true keeps the message for browsing,"
                            + " false skips it. The filter only chooses what is browsed: the route that consumes from the"
                            + " endpoint gets every message. If not set, every message is kept.")
    private Predicate<Exchange> filter;

    private final Lock limitLock = new ReentrantLock();
    // volatile: doInit or getExchanges can set the list while producer threads add to it
    private volatile List<Exchange> exchanges;
    private volatile Processor onExchangeProcessor;

    public BrowseEndpoint() {
    }

    public BrowseEndpoint(String uri, Component component) {
        super(uri, component);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public int getBrowseLimit() {
        return browseLimit;
    }

    @Override
    public void setBrowseLimit(int browseLimit) {
        this.browseLimit = browseLimit;
    }

    @Override
    public List<Exchange> getExchanges() {
        if (exchanges == null) {
            exchanges = createExchangeList();
        }
        return exchanges;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {
            public void process(Exchange exchange) throws Exception {
                onExchange(exchange);
            }
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new BrowseConsumer(processor);
        configureConsumer(answer);
        return answer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Predicate<Exchange> getFilter() {
        return filter;
    }

    public void setFilter(Predicate<Exchange> filter) {
        this.filter = filter;
    }

    protected List<Exchange> createExchangeList() {
        return new CopyOnWriteArrayList<>();
    }

    /**
     * Invoked on a message exchange being sent by a producer
     *
     * @param  exchange  the exchange
     * @throws Exception is thrown if failed to process the exchange
     */
    protected void onExchange(Exchange exchange) throws Exception {
        // the filter only chooses the exchanges that are kept for browsing: the route that consumes from this endpoint
        // gets every exchange
        if (filter == null || filter.test(exchange)) {
            addExchange(exchange);
        }

        // now fire the consumer
        Processor processor = onExchangeProcessor;
        if (processor != null) {
            processor.process(exchange);
        }
    }

    private void addExchange(Exchange exchange) {
        List<Exchange> list = exchanges;
        if (browseLimit > 0) {
            // add first and then remove the oldest from the list itself: a sub list is only a view that keeps them
            // all in memory, and a reader never sees fewer than the limit once it was reached. Under a lock, as
            // concurrent producers would otherwise remove too many or overtake the limit
            limitLock.lock();
            try {
                list.add(exchange);
                int excess = list.size() - browseLimit;
                if (excess > 0) {
                    list.subList(0, excess).clear();
                }
            } finally {
                limitLock.unlock();
            }
        } else {
            list.add(exchange);
        }
    }

    /**
     * The route of the consumer gets the exchanges only while the consumer is started: a stopped or removed route no
     * longer gets them, and the producers that send to this endpoint keep working.
     */
    private final class BrowseConsumer extends DefaultConsumer {

        BrowseConsumer(Processor processor) {
            super(BrowseEndpoint.this, processor);
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            onExchangeProcessor = getProcessor();
        }

        @Override
        protected void doStop() throws Exception {
            if (onExchangeProcessor == getProcessor()) {
                onExchangeProcessor = null;
            }
            super.doStop();
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        // the endpoint is initialized again when it is started after a stop, such as when the route that consumes from
        // it is added again: keep the exchanges the routes that send to it browsed meanwhile
        if (exchanges == null) {
            exchanges = createExchangeList();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // the endpoint is also stopped when the route that consumes from it is removed (such as on a reload in dev
        // mode), while the routes that send to it keep sending: their exchanges stay available for browsing, and are
        // only cleared when CamelContext is stopping
        List<Exchange> list = exchanges;
        if (list != null && getCamelContext().isStopping()) {
            list.clear();
        }
        super.doStop();
    }
}
