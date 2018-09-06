/**
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
package org.apache.camel.component.google.calendar.stream;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GoogleCalendar consumer.
 */
public class GoogleCalendarStreamConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarStreamConsumer.class);

    public GoogleCalendarStreamConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    protected GoogleCalendarStreamConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Calendar getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public GoogleCalendarStreamEndpoint getEndpoint() {
        return (GoogleCalendarStreamEndpoint)super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        Date date = new Date();
        com.google.api.services.calendar.Calendar.Events.List request = getClient().events().list("primary").setOrderBy("updated").setTimeMin(new DateTime(date));
        if (ObjectHelper.isNotEmpty(getConfiguration().getQuery())) {
            request.setQ(getConfiguration().getQuery());
        }
        if (ObjectHelper.isNotEmpty(getConfiguration().getMaxResults())) {
            request.setMaxResults(getConfiguration().getMaxResults());
        }

        Queue<Exchange> answer = new LinkedList<>();

        Events c = request.execute();

        if (c != null) {
            List<Event> list = c.getItems();
            for (Event event : list) {
                Exchange exchange = getEndpoint().createExchange(getEndpoint().getExchangePattern(), event);
                answer.add(exchange);
            }
        }

        return processBatch(CastUtils.cast(answer));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange done");
                }
            });
        }

        return total;
    }

}
