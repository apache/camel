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
package org.apache.camel.component.geocoder;

import java.math.BigDecimal;

import com.google.code.geocoder.Geocoder;
import com.google.code.geocoder.model.GeocodeResponse;
import com.google.code.geocoder.model.GeocoderAddressComponent;
import com.google.code.geocoder.model.GeocoderRequest;
import com.google.code.geocoder.model.GeocoderResult;
import com.google.code.geocoder.model.GeocoderStatus;
import com.google.code.geocoder.model.LatLng;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GeoCoder producer.
 */
public class GeoCoderProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(GeoCoderProducer.class);
    private GeoCoderEndpoint endpoint;

    private final Geocoder geocoder = new Geocoder();

    public GeoCoderProducer(GeoCoderEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        // header take precedence
        String address = exchange.getIn().getHeader(GeoCoderConstants.ADDRESS, String.class);
        if (address == null) {
            address = endpoint.getAddress();
        }

        String latlng = exchange.getIn().getHeader(GeoCoderConstants.LATLNG, String.class);
        if (latlng == null) {
            latlng = endpoint.getLatlng();
        }

        if (latlng != null) {
            GeocoderRequest req = new GeocoderRequest();
            req.setLanguage(endpoint.getLanguage());

            String lat = ObjectHelper.before(latlng, ",");
            String lng = ObjectHelper.after(latlng, ",");
            req.setLocation(new LatLng(lat, lng));

            LOG.debug("Geocode for lat/lng {}", latlng);
            GeocodeResponse res = geocoder.geocode(req);
            LOG.debug("Geocode response {}", res);

            if (res != null) {
                extractGeoResult(res, exchange);
            }
        } else if (address != null) {
            GeocoderRequest req = new GeocoderRequest(address, endpoint.getLanguage());
            LOG.debug("Geocode for address {}", address);
            GeocodeResponse res = geocoder.geocode(req);
            LOG.debug("Geocode response {}", res);

            if (res != null) {
                extractGeoResult(res, exchange);
            }
        }
    }

    protected void extractGeoResult(GeocodeResponse res, Exchange exchange) {
        exchange.getIn().setHeader(GeoCoderConstants.STATUS, res.getStatus());
        exchange.getIn().setBody(res);

        if (res.getStatus() == GeocoderStatus.OK) {
            exchange.getIn().setHeader(GeoCoderConstants.ADDRESS, res.getResults().get(0).getFormattedAddress());
            // just grab the first element and its lat and lon
            BigDecimal resLat = res.getResults().get(0).getGeometry().getLocation().getLat();
            BigDecimal resLon = res.getResults().get(0).getGeometry().getLocation().getLng();
            String resLatlng = resLat.toPlainString() + "," + resLon.toPlainString();
            exchange.getIn().setHeader(GeoCoderConstants.LATLNG, resLatlng);

            GeocoderAddressComponent country = getCountry(res);
            if (country != null) {
                exchange.getIn().setHeader(GeoCoderConstants.COUNTRY_SHORT, country.getShortName());
                exchange.getIn().setHeader(GeoCoderConstants.COUNTRY_LONG, country.getLongName());
            }
        }
    }

    private static GeocoderAddressComponent getCountry(GeocodeResponse res) {
        for (GeocoderResult result : res.getResults()) {
            // look for "country" in types
            for (String type : result.getTypes()) {
                if ("country".equals(type)) {
                    return result.getAddressComponents().get(0);
                }
            }
        }
        return null;
    }

}
