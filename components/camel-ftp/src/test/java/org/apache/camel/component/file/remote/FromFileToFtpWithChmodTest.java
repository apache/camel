/*
 * Copyright (c): it@M - Dienstleister für Informations- und Telekommunikationstechnik
 * der Landeshauptstadt München, 2016
 */
package org.apache.camel.component.file.remote;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @author eidottermihi
 */
public class FromFileToFtpWithChmodTest extends FtpServerTestSupport {
    
    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/tmp2/camel?password=admin&consumer.initialDelay=3000&chmod=777";
    }

    @Test
    public void testFromFileToFtp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).to("mock:result");
                from("file:src/main/data?noop=true&consumer.delay=3000").to(getFtpUrl());
            }
        };
    }
}
