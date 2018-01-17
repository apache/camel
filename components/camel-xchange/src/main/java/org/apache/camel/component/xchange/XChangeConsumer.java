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
package org.apache.camel.component.xchange;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XChangeConsumer extends ScheduledPollConsumer {
    
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 60 * 1000L;
    
    private static final Logger LOG = LoggerFactory.getLogger(XChangeConsumer.class);

    private final MarketDataService marketService;
    
    public XChangeConsumer(XChangeEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        
        marketService = endpoint.getXChange().getMarketDataService();
    }

    @Override
    public XChangeEndpoint getEndpoint() {
        return (XChangeEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        CurrencyPair pair = getEndpoint().getConfiguration().getCurrencyPair();
        LOG.info("Going to execute ticker query for {}", pair);
        
        Ticker ticker = marketService.getTicker(pair);
        
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(ticker);
        getProcessor().process(exchange);
        
        return 1;
    }

}