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
package ${package}.incident;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Processor for processing the report incident.
 */
public class ReportIncidentProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // get the id of the input
        String id = exchange.getIn().getBody(InputReportIncident.class).getIncidentId();

        // set reply including the id
        OutputReportIncident output = new OutputReportIncident();
        output.setCode("OK;" + id);
        exchange.getOut().setBody(output);
    }

}
