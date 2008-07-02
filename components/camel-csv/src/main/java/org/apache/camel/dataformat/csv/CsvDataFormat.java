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
package org.apache.camel.dataformat.csv;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVStrategy;
import org.apache.commons.csv.writer.CSVConfig;
import org.apache.commons.csv.writer.CSVField;
import org.apache.commons.csv.writer.CSVWriter;

/**
 * @version $Revision$
 */
public class CsvDataFormat implements DataFormat {
    private CSVStrategy strategy = CSVStrategy.DEFAULT_STRATEGY;
    private CSVConfig config = new CSVConfig();

    public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
        Map map = ExchangeHelper.convertToMandatoryType(exchange, Map.class, object);
        OutputStreamWriter out = new OutputStreamWriter(outputStream);
        try {
            CSVConfig conf = getConfig();
            // lets add fields
            Set set = map.keySet();
            for (Object value : set) {
                if (value != null) {
                    String text = value.toString();
                    CSVField field = new CSVField(text);
                    conf.addField(field);
                }
            }
            CSVWriter writer = new CSVWriter(conf);
            writer.setWriter(out);
            writer.writeRecord(map);
        } finally {
            out.close();
        }
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        InputStreamReader in = new InputStreamReader(inputStream);
        try {
            CSVParser parser = new CSVParser(in, getStrategy());
            List<List<String>> list = new ArrayList<List<String>>();
            while (true) {
                String[] strings = parser.getLine();
                if (strings == null) {
                    break;
                }
                List<String> line = Arrays.asList(strings);
                list.add(line);
            }
            if (list.size() == 1) {
                return list.get(0);
            } else {
                return list;
            }
        } finally {
            in.close();
        }
    }

    public CSVConfig getConfig() {
        if (config == null) {
            config = createConfig();
        }
        return config;
    }

    public void setConfig(CSVConfig config) {
        this.config = config;
    }

    public CSVStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(CSVStrategy strategy) {
        this.strategy = strategy;
    }

    protected CSVConfig createConfig() {
        return new CSVConfig();
    }
}
