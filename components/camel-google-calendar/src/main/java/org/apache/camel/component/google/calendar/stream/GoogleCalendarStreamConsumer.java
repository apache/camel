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
package org.apache.camel.component.google.calendar.stream;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * The GoogleCalendar consumer.
 */
public class GoogleCalendarStreamConsumer extends ScheduledBatchPollingConsumer {

    private DateTime lastUpdate;

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
        com.google.api.services.calendar.Calendar.Events.List request = getClient().events().list(getConfiguration().getCalendarId()).setOrderBy("updated");
        if (ObjectHelper.isNotEmpty(getConfiguration().getQuery())) {
            request.setQ(getConfiguration().getQuery());
        }
        if (ObjectHelper.isNotEmpty(getConfiguration().getMaxResults())) {
            request.setMaxResults(getConfiguration().getMaxResults());
        }
        if (getConfiguration().isConsumeFromNow()) {
            Date date = new Date();
            request.setTimeMin(new DateTime(date));
        }
        if (getConfiguration().isConsiderLastUpdate()) {
            if (ObjectHelper.isNotEmpty(lastUpdate)) {
                request.setUpdatedMin(lastUpdate);
            }
        }

        Queue<Exchange> answer = new LinkedList<>();
        List<Date> dateList = new ArrayList<>();

        Events c = request.execute();

        if (c != null) {
            List<Event> list = c.getItems();

            for (Event event : list) {
                Exchange exchange = getEndpoint().createExchange(getEndpoint().getExchangePattern(), event);
                answer.add(exchange);
                dateList.add(new Date(event.getUpdated().getValue()));
            }
        }

        lastUpdate = retrieveLastUpdateDate(dateList);
        return processBatch(CastUtils.cast(answer));
    }

    private DateTime retrieveLastUpdateDate(List<Date> dateList) {
        Date finalLastUpdate;
        if (!dateList.isEmpty()) {
            dateList.sort((o1, o2) -> o1.compareTo(o2));
            Date lastUpdateDate = dateList.get(dateList.size() - 1);
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(lastUpdateDate);
            calendar.add(java.util.Calendar.SECOND, 1);
            finalLastUpdate = calendar.getTime();
        } else {
            finalLastUpdate = new Date();
        }
        return new DateTime(finalLastUpdate);
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

            getAsyncProcessor().process(exchange, doneSync -> {
                // noop
            });
        }
        return total;
    }

}
