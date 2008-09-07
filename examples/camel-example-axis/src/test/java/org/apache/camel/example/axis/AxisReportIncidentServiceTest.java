package org.apache.camel.example.axis;

import junit.framework.TestCase;
import org.apache.camel.example.reportincident.InputReportIncident;
import org.apache.camel.example.reportincident.OutputReportIncident;
import org.apache.camel.example.reportincident.ReportIncidentService_PortType;
import org.apache.camel.example.reportincident.ReportIncidentService_ServiceLocator;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.webapp.WebAppContext;

import java.io.File;
import java.net.URL;

/**
 * Unit test with embedded Jetty to execute a webservice request using Axis
 */
public class AxisReportIncidentServiceTest extends TestCase {

    private Server server;

    private void startJetty() throws Exception {
        // create an embedded Jetty server
        server = new Server();

        // add a listener on port 8080 on localhost (127.0.0.1)
        Connector connector = new SelectChannelConnector();
        connector.setPort(8080);
        connector.setHost("127.0.0.1");
        server.addConnector(connector);

        // add our web context path
        WebAppContext wac = new WebAppContext();
        wac.setContextPath("/unittest");
        // set the location of the exploded webapp where WEB-INF is located
        // this is a nice feature of Jetty where we can point to src/main/webapp
        wac.setWar("./src/main/webapp");
        server.setHandler(wac);

        // then start Jetty
        server.setStopAtShutdown(true);
        server.start();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        startJetty();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        server.stop();
    }

    public void testReportIncidentWithAxis() throws Exception {
        // the url to the axis webservice exposed by jetty
        URL url = new URL("http://localhost:8080/unittest/services/ReportIncidentPort");

        // Axis stuff to get the port where we can send the webservice request
        ReportIncidentService_ServiceLocator locator = new ReportIncidentService_ServiceLocator();
        ReportIncidentService_PortType port = locator.getReportIncidentPort(url);

        // create input to send
        InputReportIncident input = createDummyIncident();
        // send the webservice and get the response
        OutputReportIncident output = port.reportIncident(input);
        assertEquals("OK", output.getCode());

        // should generate a file also
        File file = new File("target/" + input.getIncidentId() + ".txt");
        assertTrue("File should exists", file.exists());
    }

    protected InputReportIncident createDummyIncident() {
        InputReportIncident input = new InputReportIncident();
        input.setEmail("davsclaus@apache.org");
        input.setIncidentId("12345678");
        input.setIncidentDate("2008-07-13");
        input.setPhone("+45 2962 7576");
        input.setSummary("Failed operation");
        input.setDetails("The wrong foot was operated.");
        input.setFamilyName("Ibsen");
        input.setGivenName("Claus");
        return input;
    }

}
