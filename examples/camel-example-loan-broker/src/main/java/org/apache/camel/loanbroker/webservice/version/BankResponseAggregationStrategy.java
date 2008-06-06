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
package org.apache.camel.loanbroker.webservice.version;

import org.apache.camel.Exchange;
import org.apache.camel.loanbroker.webservice.version.bank.BankQuote;
import org.apache.camel.processor.aggregate.AggregationStrategy;

//START SNIPPET: aggregating
public class BankResponseAggregationStrategy implements AggregationStrategy {

    public static final String BANK_QUOTE = "bank_quote";

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // Get the bank quote instance from the exchange
        BankQuote oldQuote = oldExchange.getProperty(BANK_QUOTE, BankQuote.class);
        // Get the oldQute from out message body if we can't get it from the exchange
        if (oldQuote == null) {
            Object[] oldResult = (Object[])oldExchange.getOut().getBody();
            oldQuote = (BankQuote) oldResult[0];
        }
        // Get the newQuote
        Object[] newResult = (Object[])newExchange.getOut().getBody();
        BankQuote newQuote = (BankQuote) newResult[0];
        Exchange result = null;
        BankQuote bankQuote;

        if (newQuote.getRate() >= oldQuote.getRate()) {
            result = oldExchange;
            bankQuote = oldQuote;
        } else {
            result = newExchange;
            bankQuote = newQuote;
        }
        // Set the lower rate BankQuote instance back to aggregated exchange
        result.setProperty(BANK_QUOTE, bankQuote);
        // Set the return message for the client
        result.getOut().setBody("The best rate is " + bankQuote.toString());

        return result;

    }

}
//END SNIPPET: aggregating
