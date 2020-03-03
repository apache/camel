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

import java.util.List;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.sheets.GoogleSheetsClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * The google-sheets-stream component provides access to Google Sheets.
 */
@UriEndpoint(firstVersion = "2.23.0",
             scheme = "google-sheets-stream",
             title = "Google Sheets Stream",
             syntax = "google-sheets-stream:apiName",
             consumerOnly = true,
             label = "api,cloud,sheets")
public class GoogleSheetsStreamEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private GoogleSheetsStreamConfiguration configuration;

    public GoogleSheetsStreamEndpoint(String uri, GoogleSheetsStreamComponent component, GoogleSheetsStreamConfiguration endpointConfiguration) {
        super(uri, component);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("The camel google sheets stream component doesn't support producer");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final GoogleSheetsStreamConsumer consumer = new GoogleSheetsStreamConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Sheets getClient() {
        return ((GoogleSheetsStreamComponent)getComponent()).getClient(configuration);
    }

    public GoogleSheetsClientFactory getClientFactory() {
        return ((GoogleSheetsStreamComponent)getComponent()).getClientFactory();
    }

    public void setClientFactory(GoogleSheetsClientFactory clientFactory) {
        ((GoogleSheetsStreamComponent)getComponent()).setClientFactory(clientFactory);
    }

    public GoogleSheetsStreamConfiguration getConfiguration() {
        return configuration;
    }

    public Exchange createExchange(int rangeIndex, ValueRange valueRange) {
        Exchange exchange = super.createExchange(getExchangePattern());
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID, configuration.getSpreadsheetId());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE, valueRange.getRange());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE_INDEX, rangeIndex);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION, valueRange.getMajorDimension());
        message.setBody(valueRange);
        return exchange;
    }

    public Exchange createExchange(int rangeIndex, int valueIndex, String range, String majorDimension, List<Object> values) {
        Exchange exchange = super.createExchange(getExchangePattern());
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID, configuration.getSpreadsheetId());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE_INDEX, rangeIndex);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.VALUE_INDEX, valueIndex);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.RANGE, range);
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION, majorDimension);
        message.setBody(values);
        return exchange;
    }

    public Exchange createExchange(Spreadsheet spreadsheet) {
        Exchange exchange = super.createExchange(getExchangePattern());
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID, spreadsheet.getSpreadsheetId());
        exchange.getIn().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_URL, spreadsheet.getSpreadsheetUrl());
        message.setBody(spreadsheet);
        return exchange;
    }
}
