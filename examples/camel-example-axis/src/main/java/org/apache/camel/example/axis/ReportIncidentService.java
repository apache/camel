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