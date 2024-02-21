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
package org.apache.camel.component.google.sheets.stream;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * The GoogleSheets consumer.
 */
public class GoogleSheetsStreamConsumer extends ScheduledBatchPollingConsumer {
    public GoogleSheetsStreamConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    protected GoogleSheetsStreamConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Sheets getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public GoogleSheetsStreamEndpoint getEndpoint() {
        return (GoogleSheetsStreamEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        Queue<Exchange> answer = new ArrayDeque<>();

        if (ObjectHelper.isNotEmpty(getConfiguration().getRange())) {
            Sheets.Spreadsheets.Values.BatchGet request
                    = getClient().spreadsheets().values().batchGet(getConfiguration().getSpreadsheetId());

            request.setMajorDimension(getConfiguration().getMajorDimension());
            request.setValueRenderOption(getConfiguration().getValueRenderOption());

            if (getConfiguration().getRange().contains(",")) {
                request.setRanges(Arrays.stream(getConfiguration().getRange().split(","))
                        .map(String::trim)
                        .collect(Collectors.toList()));
            } else {
                request.setRanges(Collections.singletonList(getConfiguration().getRange()));
            }

            BatchGetValuesResponse response = request.execute();

            // okay we have some response from Google so lets mark the consumer as ready
            forceConsumerAsReady();

            if (response.getValueRanges() != null) {
                if (getConfiguration().isSplitResults()) {
                    for (ValueRange valueRange : response.getValueRanges()) {
                        AtomicInteger rangeIndex = new AtomicInteger(1);
                        AtomicInteger valueIndex = new AtomicInteger();
                        if (getConfiguration().getMaxResults() > 0) {
                            valueRange.getValues().stream()
                                    .limit(getConfiguration().getMaxResults())
                                    .map(values -> createExchange(rangeIndex.get(), valueIndex.incrementAndGet(),
                                            valueRange.getRange(), valueRange.getMajorDimension(), values))
                                    .forEach(answer::add);
                        } else {
                            valueRange.getValues().stream()
                                    .map(values -> createExchange(rangeIndex.get(), valueIndex.incrementAndGet(),
                                            valueRange.getRange(), valueRange.getMajorDimension(), values))
                                    .forEach(answer::add);
                        }
                        rangeIndex.incrementAndGet();
                    }
                } else {
                    AtomicInteger rangeIndex = new AtomicInteger();
                    response.getValueRanges()
                            .stream()
                            .peek(valueRange -> {
                                if (getConfiguration().getMaxResults() > 0) {
                                    valueRange.setValues(valueRange.getValues()
                                            .stream()
                                            .limit(getConfiguration().getMaxResults())
                                            .collect(Collectors.toList()));
                                }
                            })
                            .map(valueRange -> createExchange(rangeIndex.incrementAndGet(), valueRange))
                            .forEach(answer::add);
                }
            }
        } else {
            Sheets.Spreadsheets.Get request = getClient().spreadsheets().get(getConfiguration().getSpreadsheetId());

            request.setIncludeGridData(getConfiguration().isIncludeGridData());

            Spreadsheet spreadsheet = request.execute();
            answer.add(createExchange(spreadsheet));
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
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }

        return total;
    }

    public Exchange createExchange(int rangeIndex, ValueRange valueRange) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(getEndpoint().getExchangePattern());
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID,
                getEndpoint().getConfiguration().getSpreadsheetId());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE, valueRange.getRange());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE_INDEX, rangeIndex);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION, valueRange.getMajorDimension());
        message.setBody(valueRange);
        return exchange;
    }

    public Exchange createExchange(int rangeIndex, int valueIndex, String range, String majorDimension, List<Object> values) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(getEndpoint().getExchangePattern());
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID,
                getEndpoint().getConfiguration().getSpreadsheetId());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE_INDEX, rangeIndex);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.VALUE_INDEX, valueIndex);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE, range);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION, majorDimension);
        message.setBody(values);
        return exchange;
    }

    public Exchange createExchange(Spreadsheet spreadsheet) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(getEndpoint().getExchangePattern());
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID, spreadsheet.getSpreadsheetId());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_URL, spreadsheet.getSpreadsheetUrl());
        message.setBody(spreadsheet);
        return exchange;
    }

}
