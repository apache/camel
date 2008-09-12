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
package org.apache.camel.example.axis;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.example.reportincident.InputReportIncident;
import org.apache.camel.example.reportincident.OutputReportIncident;

/**
 * Our real service that is not tied to Axis
 */
public class ReportIncidentService {

    @EndpointInject(name = "backup")
    private ProducerTemplate template;

    public OutputReportIncident reportIncident(InputReportIncident parameters) {
        System.out.println("Hello ReportIncidentService is called from " + parameters.getGivenName());

        String data = parameters.getDetails();

        // store the data as a file
        String filename = parameters.getIncidentId() + ".txt";
        // send the data to the endpoint and the header contains what filename it should be stored as
        template.sendBodyAndHeader(data, "org.apache.camel.file.name", filename);

        OutputReportIncident out = new OutputReportIncident();
        out.setCode("OK");
        return out;
    }

    public void setTemplate(ProducerTemplate template) {
        this.template = template;
    }

}