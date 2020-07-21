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
package org.apache.camel.dataformat.any23;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.Any23Type;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Any23DataFormatConfigurationsTest extends CamelTestSupport {

    private final String baseURI = "http://mock.foo/bar";

    @Test
    public void test() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        String contenhtml = Any23TestSupport.loadFileAsString(new File("src/test/resources/org/apache/camel/dataformat/any23/microformat/vcard.html"));
        template.sendBody("direct:start", contenhtml);
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            String resultingRDF = in.getBody(String.class);
            InputStream toInputStream = IOUtils.toInputStream(resultingRDF, Charset.defaultCharset());
            Model parse = Rio.parse(toInputStream, baseURI, RDFFormat.TURTLE);
            assertEquals(parse.size(), 25);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                Map<String, String> conf = new HashMap<>();
                conf.put("any23.extraction.metadata.nesting", "off");
                from("direct:start").unmarshal().any23(baseURI, Any23Type.TURTLE, conf).to("mock:result");
            }
        };
    }

}
