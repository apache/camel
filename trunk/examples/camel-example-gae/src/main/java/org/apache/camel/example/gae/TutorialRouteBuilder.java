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
package org.apache.camel.example.gae;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.gae.mail.GMailBinding;
import org.apache.camel.processor.aggregate.AggregationStrategy;


public class TutorialRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("ghttp:///weather")
            .process(new RequestProcessor())
            .marshal().serialization()
            .to("gtask://default")
            .unmarshal().serialization()
            .process(new ResponseProcessor());
      
        from("gtask://default")
            .unmarshal().serialization()
            .setHeader(Exchange.HTTP_QUERY, constant("weather=").append(ReportData.city()))
            .enrich("ghttp://www.google.com/ig/api", reportDataAggregator())
            .setHeader(GMailBinding.GMAIL_SUBJECT, constant("Weather report"))
            .setHeader(GMailBinding.GMAIL_SENDER, ReportData.requestor())
            .setHeader(GMailBinding.GMAIL_TO, ReportData.recipient())
            .process(new ReportGenerator())        
            .to("gmail://default");
    }

    private static AggregationStrategy reportDataAggregator() {
        return new AggregationStrategy() {
            public Exchange aggregate(Exchange reportExchange, Exchange weatherExchange) {
                ReportData reportData = reportExchange.getIn().getBody(ReportData.class);
                reportData.setWeather(toDocument(weatherExchange.getIn().getBody(InputStream.class)));
                return reportExchange;
            }
        };
    }
    
    // As GAE have trouble to load the  javax.xml.transform.stax.StAXSource which is used the XmlConverter
    // Now we just do the transformation ourself.
    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        return factory;
    }
    
    private static Document toDocument(InputStream stream) {
        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        try {
            Document doc =  factory.newDocumentBuilder().parse(stream);
            return doc;
        } catch (Exception ex) {
            throw new RuntimeCamelException(ex);
        }
    }
    
}
