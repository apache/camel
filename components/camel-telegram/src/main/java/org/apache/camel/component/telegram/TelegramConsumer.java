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
package org.apache.camel.component.telegram;

import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.Update;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.impl.ScheduledPollConsumer;

/**
 * A polling consumer that reads messages from a chat using the Telegram bot API.
 */
public class TelegramConsumer extends ScheduledPollConsumer {

    private TelegramEndpoint endpoint;

    /**
     * Holds the current offset, used for retrieving incremental updates.
     */
    private volatile Long offset;

    public TelegramConsumer(TelegramEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {

        TelegramConfiguration config = endpoint.getConfiguration();

        Long realOffset = offset != null ? offset : 0L;

        TelegramService service = TelegramServiceProvider.get().getService();

        log.debug("Polling Telegram service to get updates");

        UpdateResult updateResult = service.getUpdates(config.getAuthorizationToken(), offset, config.getLimit(), config.getTimeout());
        if (updateResult.getUpdates() == null) {
            // to simplify processing
            updateResult.setUpdates(Collections.emptyList());
        }

        if (!updateResult.isOk()) {
            throw new IllegalStateException("The server was unable to process the request. Response was " + updateResult);
        }

        List<Update> updates = updateResult.getUpdates();

        if (updates.size() > 0) {
            log.debug("Received {} updates from Telegram service", updates.size());
        } else {
            log.debug("No updates received from Telegram service");
        }

        processUpdates(updates);

        // update offset to prevent retrieving the same data again
        updateOffset(updates);

        return updates.size();
    }

    private void processUpdates(List<Update> updates) throws Exception {
        for (Update update : updates) {

            log.debug("Received update from Telegram service: {}", update);

            Exchange exchange = endpoint.createExchange(update);
            getProcessor().process(exchange);
        }
    }


    private void updateOffset(List<Update> updates) {
        OptionalLong ol = updates.stream().mapToLong(Update::getUpdateId).max();
        if (ol.isPresent()) {
            this.offset = ol.getAsLong() + 1;
            log.debug("Next Telegram offset will be {}", this.offset);
        }
    }
}
