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

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.StringHelper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GeoCoder producer for Nominatim.
 */
public class GeoCoderNominatimProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(GeoCoderNominatimProducer.class);

    private final GeoCoderEndpoint endpoint;
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    public GeoCoderNominatimProducer(GeoCoderEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        httpClient.close();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // headers take precedence
        String address = exchange.getIn().getHeader(GeoCoderConstants.ADDRESS, String.class);
        if (address == null) {
            address = endpoint.getAddress();
        }

        String latlng = exchange.getIn().getHeader(GeoCoderConstants.LATLNG, String.class);
        if (latlng == null) {
            latlng = endpoint.getLatlng();
        }

        String geocoded = null;

        if (latlng != null) {
            String lat = StringHelper.before(latlng, ",");
            String lng = StringHelper.after(latlng, ",");

            LOG.debug("Geocode for lat/lng {}", latlng);
            geocoded = query(lat, lng);
        } else if (address != null) {
            LOG.debug("Geocode for address {}", address);
            geocoded = query(address);
        }

        LOG.debug("Geocode response {}", geocoded);

        extractResult(geocoded, exchange);
    }

    private String query(String dlat, String dlon) throws IOException {

        Map<String, String> params = new HashMap<>();
        params.put("format", "jsonv2");
        params.put("lat", dlat);
        params.put("lon", dlon);

        return queryForString("reverse", params);
    }

    private String query(String address) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("format", "jsonv2");
        params.put("addressdetails", "1");
        params.put("q", address);
        params.put("limit", "1");

        return queryForString("search", params);
    }

    private String queryForString(String operation, Map<String, String> params) throws IOException {
        String url = endpoint.getServerUrl();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += operation;

        final ClassicRequestBuilder builder = ClassicRequestBuilder.get().setUri(url);

        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.addParameter(entry.getKey(), entry.getValue());
        }

        return httpClient.execute(builder.build(), resp -> EntityUtils.toString(resp.getEntity()));
    }

    protected void extractResult(String place, Exchange exchange) {
        if (!endpoint.isHeadersOnly()) {
            exchange.getIn().setBody(place);
        }

        if (place == null || place.isEmpty()) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);
            return;
        }
        exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.OK);

        if (place.startsWith("[") && place.endsWith("]")) {
            place = place.substring(1, place.length() - 1);
        }

        // additional details
        final Configuration conf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
        final DocumentContext doc = JsonPath.using(conf).parse(place);

        exchange.getIn().setHeader(GeoCoderConstants.ADDRESS, doc.read("$['display_name']"));

        // just grab the first element and its lat and lon
        setLatLngToExchangeHeader(doc.read("$['lat']"), doc.read("$['lon']"), exchange);

        extractCountry(doc, exchange.getIn());
        extractCity(doc, exchange.getIn());
        extractPostalCode(doc, exchange.getIn());
        extractRegion(doc, exchange.getIn());
    }

    private void setLatLngToExchangeHeader(String resLat, String resLng, Exchange exchange) {
        exchange.getIn().setHeader(GeoCoderConstants.LAT, formatLatOrLon(resLat));
        exchange.getIn().setHeader(GeoCoderConstants.LNG, formatLatOrLon(resLng));
        String resLatlng = formatLatOrLon(resLat) + ", " + formatLatOrLon(resLng);
        exchange.getIn().setHeader(GeoCoderConstants.LATLNG, resLatlng);
    }

    private void extractCountry(DocumentContext doc, Message in) {
        String code = doc.read("$['address']['country_code']");
        if (code != null) {
            code = code.toUpperCase();
        }
        in.setHeader(GeoCoderConstants.COUNTRY_SHORT, code);
        in.setHeader(GeoCoderConstants.COUNTRY_LONG, doc.read("$['address']['country']"));
    }

    private void extractCity(DocumentContext doc, Message in) {
        in.setHeader(GeoCoderConstants.CITY, doc.read("$['address']['city']"));
    }

    private void extractPostalCode(DocumentContext doc, Message in) {
        in.setHeader(GeoCoderConstants.POSTAL_CODE, doc.read("$['address']['postcode']"));
    }

    private void extractRegion(DocumentContext doc, Message in) {
        String code = doc.read("$['address']['state_code']");
        if (code != null) {
            code = code.toUpperCase();
        }
        in.setHeader(GeoCoderConstants.REGION_CODE, code);
        in.setHeader(GeoCoderConstants.REGION_NAME, doc.read("$['address']['state']"));
    }

    private String formatLatOrLon(String value) {
        return String.format(Locale.ENGLISH, "%.8f", Double.parseDouble(value));
    }
}
