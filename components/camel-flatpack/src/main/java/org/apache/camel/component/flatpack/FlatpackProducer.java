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
package org.apache.camel.component.flatpack;

import net.sf.flatpack.DataSet;
import net.sf.flatpack.Parser;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

class FlatpackProducer extends DefaultProducer {
    private FlatpackEndpoint endpoint;

    FlatpackProducer(FlatpackEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        Parser parser = endpoint.createParser(exchange);
        DataSet dataSet = parser.parse();

        if (dataSet.getErrorCount() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Flatpack has found %s errors while parsing", dataSet.getErrorCount()));
            throw new FlatpackException(sb.toString(), exchange, dataSet.getErrors());
        }

        if (endpoint.isSplitRows()) {
            int counter = 0;
            while (dataSet.next()) {
                endpoint.processDataSet(exchange, dataSet, counter++);
            }
        } else {
            endpoint.processDataSet(exchange, dataSet, dataSet.getRowCount());
        }
    }
}
