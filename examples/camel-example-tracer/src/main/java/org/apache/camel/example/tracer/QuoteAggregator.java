package org.apache.camel.example.tracer;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.Exchange;

/**
 *
 */
public class QuoteAggregator implements AggregationStrategy {

    private List<String> coolWords = new ArrayList<String>();

    public void setCoolWords(List<String> coolWords) {
        for (String s : coolWords) {
            this.coolWords.add(s.toLowerCase());
        }
        Collections.reverse(this.coolWords);
    }

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        String oldQute = oldExchange.getIn().getBody(String.class);
        String newQute = newExchange.getIn().getBody(String.class);

        int result = new QuoteComparator().compare(oldQute, newQute);

        return result > 0 ? newExchange : oldExchange;
    }

    private class QuoteComparator implements Comparator<String> {

        public int compare(java.lang.String o1, java.lang.String o2) {
            int index1 = coolWords.indexOf(o1.toLowerCase());
            int index2 = coolWords.indexOf(o2.toLowerCase());

            return index1 - index2;
        }
    }

}
