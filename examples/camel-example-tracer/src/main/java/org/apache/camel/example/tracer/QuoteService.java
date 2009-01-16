package org.apache.camel.example.tracer;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 */
public class QuoteService {

    private List<String> quotes = new ArrayList<String>();

    public void setQuotes(List<String> quotes) {
        this.quotes = quotes;
    }

    public String quote(String text) {
        for (String s : quotes) {
            if (s.toLowerCase().contains(text.toLowerCase())) {
                return s;
            }
        }
        return "No quote found for the input: " + text;
    }

    public List<String> splitWords(String payload) {
        return Arrays.asList(payload.split(" "));
    }


}
