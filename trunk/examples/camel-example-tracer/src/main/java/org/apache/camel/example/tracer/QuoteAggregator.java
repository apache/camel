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
package org.apache.camel.example.tracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * Our aggregator where we aggregate all the quotes and find the
 * the best quotes based on the one that has the most cool words
 * from our cools words list
 */
public class QuoteAggregator implements AggregationStrategy {

    private List<String> coolWords = new ArrayList<String>();

    public void setCoolWords(List<String> coolWords) {
        for (String s : coolWords) {
            // use lower case to be case insensitive
            this.coolWords.add(s.toLowerCase());
        }
        // reverse order so indexOf returning -1 will be the last instead
        Collections.reverse(this.coolWords);
    }

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            // the first time then just return the new exchange
            return newExchange;
        }

        // here we aggregate
        // oldExchange is the current "winner"
        // newExchange is the new candidate

        // we get the quotes of the two exchanges
        String oldQuote = oldExchange.getIn().getBody(String.class);
        String newQuote = newExchange.getIn().getBody(String.class);

        // now we compare the two and get a result indicate the best one
        int result = new QuoteComparator().compare(oldQuote, newQuote);

        // we return the winner
        return result > 0 ? newExchange : oldExchange;
    }

    private class QuoteComparator implements Comparator<String> {

        public int compare(java.lang.String o1, java.lang.String o2) {
            // here we compare the two quotes and picks the one that
            // is in the top of the cool words list
            int index1 = coolWords.indexOf(o1.toLowerCase());
            int index2 = coolWords.indexOf(o2.toLowerCase());

            return index1 - index2;
        }
    }

}
