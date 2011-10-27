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
package org.apache.camel.example.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.language.XPath;

/**
 * @version 
 */
public class StockService {

    private final List<String> symbols = new ArrayList<String>();
    private Map<String, Integer> stat = new ConcurrentHashMap<String, Integer>();

    public StockService() {
        symbols.add("IBM");
        symbols.add("APPLE");
        symbols.add("ORCL");
    }

    public String transform(@XPath("/stock/symbol/text()") String symbol, @XPath("/stock/value/text()") String value) {
        Integer hits = stat.get(symbol);
        if (hits == null) {
            hits = 1;
        } else {
            hits++;
        }
        stat.put(symbol, hits);

        return symbol + "@" + hits;
    }

    public String getHits() {
        return stat.toString();
    }

    public String createRandomStocks() {
        Random ran = new Random();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<stocks>\n");
        for (int i = 0; i < 100; i++) {
            int winner = ran.nextInt(symbols.size());
            String symbol = symbols.get(winner);
            int value = ran.nextInt(1000);
            xml.append("<stock>");
            xml.append("<symbol>").append(symbol).append("</symbol>");
            xml.append("<value>").append(value).append("</value>");
            xml.append("</stock>\n");
        }
        xml.append("</stocks>");

        return xml.toString();
    }

}
