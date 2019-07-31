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
package org.apache.camel.example.cxf.proxy;

import org.apache.camel.example.reportincident.InputReportIncident;
import org.apache.camel.example.reportincident.OutputReportIncident;
import org.apache.camel.example.reportincident.ReportIncidentEndpoint;

/**
 * This is the implementation of the real web service
 */
public class ReportIncidentEndpointService implements ReportIncidentEndpoint {

    public OutputReportIncident reportIncident(InputReportIncident in) {
        // just log and return a fixed response
        System.out.println("\n\n\nInvoked real web service: id=" + in.getIncidentId()
                + " by " + in.getGivenName() + " " + in.getFamilyName() + "\n\n\n");

        OutputReportIncident out = new OutputReportIncident();
        out.setCode("OK;" + in.getIncidentId());
        return out;
    }

}
