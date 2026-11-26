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
package org.apache.camel.component.geocoder;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeoCoderNominatimLanguageTest extends GeoCoderNominatimTestSupport {

    @Test
    public void testSearchSendsLanguage() throws Exception {
        answerFromResource("search", "search-calle-marie-curie.json");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.OK);

        template.sendBody("direct:french", "Test");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(lastQuery("search")).contains("accept-language=fr");
    }

    @Test
    public void testDefaultLanguageIsEnglish() throws Exception {
        answerFromResource("search", "search-calle-marie-curie.json");

        template.sendBody("direct:default", "Test");

        assertThat(lastQuery("search")).contains("accept-language=en");
    }

    @Test
    public void testEmptyLanguageIsNotSent() throws Exception {
        answerFromResource("search", "search-calle-marie-curie.json");

        template.sendBody("direct:noLanguage", "Test");

        assertThat(lastQuery("search")).isNotNull().doesNotContain("accept-language");
    }

    @Test
    public void testReverseSendsLanguageAndTrimmedLatLng() throws Exception {
        answerFromResource("reverse", "reverse-sevilla.json");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GeoCoderConstants.STATUS, GeocoderStatus.OK);

        // the latlng header as the producer sets it, with a space after the comma
        template.sendBodyAndHeader("direct:french", "Test", GeoCoderConstants.LATLNG, "37.38605170, -5.99255350");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(lastQuery("reverse"))
                .contains("accept-language=fr")
                .contains("lat=37.38605170")
                .contains("lon=-5.99255350")
                .doesNotContain("lon=+")
                .doesNotContain("lon=%20");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:french")
                        .to("geocoder:address:Paris?type=NOMINATIM&language=fr&serverUrl=RAW(" + serverUrl() + ")")
                        .to("mock:result");

                from("direct:default")
                        .to("geocoder:address:Paris?type=NOMINATIM&serverUrl=RAW(" + serverUrl() + ")");

                from("direct:noLanguage")
                        .to("geocoder:address:Paris?type=NOMINATIM&language=&serverUrl=RAW(" + serverUrl() + ")");
            }
        };
    }
}
