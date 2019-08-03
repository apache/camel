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

import java.util.Locale;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.GeolocationApi;
import com.google.maps.errors.InvalidRequestException;
import com.google.maps.errors.OverDailyLimitException;
import com.google.maps.errors.OverQueryLimitException;
import com.google.maps.errors.RequestDeniedException;
import com.google.maps.errors.UnknownErrorException;
import com.google.maps.model.AddressComponent;
import com.google.maps.model.AddressType;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.GeolocationPayload;
import com.google.maps.model.GeolocationResult;
import com.google.maps.model.LatLng;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GeoCoder producer.
 */
public class GeoCoderProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(GeoCoderProducer.class);

    private GeoCoderEndpoint endpoint;
    private GeoApiContext context;

    public GeoCoderProducer(GeoCoderEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            // headers take precedence
            String address = exchange.getIn().getHeader(GeoCoderConstants.ADDRESS, String.class);
            if (address == null) {
                address = endpoint.getAddress();
            }

            String latlng = exchange.getIn().getHeader(GeoCoderConstants.LATLNG, String.class);
            if (latlng == null) {
                latlng = endpoint.getLatlng();
            }

            if (latlng != null) {
                String lat = StringHelper.before(latlng, ",");
                String lng = StringHelper.after(latlng, ",");
                LatLng latLng = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));

                LOG.debug("Geocode for lat/lng {}", latlng);
                GeocodingResult[] results = GeocodingApi.reverseGeocode(context, latLng).await();

                LOG.debug("Geocode response {}", results);

                if (results != null) {
                    extractGeoResult(results, exchange);
                }

            } else if (address != null) {

                // is it current address
                if ("current".equals(address)) {
                    processCurrentLocation(exchange);
                } else {

                    LOG.debug("Geocode for address {}", address);
                    GeocodingResult[] results = GeocodingApi.geocode(context, address).await();
                    LOG.debug("Geocode response {}", results);

                    if (results != null) {
                        extractGeoResult(results, exchange);
                    }
                }
            }
        } catch (RequestDeniedException e) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.REQUEST_DENIED);
        } catch (OverQueryLimitException e) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.OVER_QUERY_LIMIT);
        } catch (OverDailyLimitException e) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.OVER_DAILY_LIMIT);
        } catch (InvalidRequestException e) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.INVALID_REQUEST);
        } catch (UnknownErrorException e) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.UNKNOWN_ERROR);
        }
    }

    /**
     * Perform geolocation to retrieve LatLng and then perform a geocoding
     */
    protected void processCurrentLocation(Exchange exchange) throws Exception {
        LOG.debug("Geolocation for current location");
        GeolocationPayload payload = new GeolocationPayload();
        payload.considerIp = true;
        GeolocationResult result = GeolocationApi.geolocate(context, payload).await();

        LOG.debug("Geolocation response {}", result);
        //status
        exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.OK);

        //latlng
        String resLatlng = result.location.toString();
        exchange.getIn().setHeader(GeoCoderConstants.LATLNG, resLatlng);

        //address - reverse geocode
        LOG.debug("Geocode - reverse geocode for location {}", resLatlng);
        GeocodingResult[] results = GeocodingApi.reverseGeocode(context, result.location).await();

        LOG.debug("Geocode response {}", results);

        if (results != null) {
            extractGeoResult(results, exchange);
        }
    }

    private void setLatLngToExchangeHeader(LatLng location, Exchange exchange) {
        double resLat = location.lat;
        double resLng = location.lng;
        exchange.getIn().setHeader(GeoCoderConstants.LAT, formatLatOrLon(resLat));
        exchange.getIn().setHeader(GeoCoderConstants.LNG, formatLatOrLon(resLng));
        String resLatlng = location.toString();
        exchange.getIn().setHeader(GeoCoderConstants.LATLNG, resLatlng);
    }

    protected void extractGeoResult(GeocodingResult[] res, Exchange exchange) {
        // should we include body
        if (!endpoint.isHeadersOnly()) {
            exchange.getIn().setBody(res);
        }
        //no results
        if (res.length == 0) {
            exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.ZERO_RESULTS);
            return;
        }

        exchange.getIn().setHeader(GeoCoderConstants.STATUS, GeocoderStatus.OK);

        GeocodingResult first = res[0];
        exchange.getIn().setHeader(GeoCoderConstants.ADDRESS, first.formattedAddress);
        // just grab the first element and its lat and lon
        setLatLngToExchangeHeader(first.geometry.location, exchange);

        //additional details
        AddressComponent country = getCountry(res);
        if (country != null) {
            exchange.getIn().setHeader(GeoCoderConstants.COUNTRY_SHORT, country.shortName);
            exchange.getIn().setHeader(GeoCoderConstants.COUNTRY_LONG, country.longName);
        }

        AddressComponent city = getCity(res);
        if (city != null) {
            exchange.getIn().setHeader(GeoCoderConstants.CITY, city.longName);
        }

        AddressComponent postalCode = getPostalCode(res);
        if (postalCode != null) {
            exchange.getIn().setHeader(GeoCoderConstants.POSTAL_CODE, postalCode.shortName);
        }

        AddressComponent region = getRegion(res);
        if (region != null) {
            exchange.getIn().setHeader(GeoCoderConstants.REGION_CODE, region.shortName);
            exchange.getIn().setHeader(GeoCoderConstants.REGION_NAME, region.longName);
        }
    }

    private String formatLatOrLon(double value) {
        return String.format(Locale.ENGLISH, "%.8f", value);
    }

    private static AddressComponent getComponent(GeocodingResult[] results, AddressType addressType) {
        for (GeocodingResult result : results) {
            for (AddressType type : result.types) {
                if (type == addressType && result.addressComponents.length > 0) {
                    return result.addressComponents[0];
                }
            }
        }
        return null;
    }

    private static AddressComponent getCountry(GeocodingResult[] res) {
        return getComponent(res, AddressType.COUNTRY);
    }

    private static AddressComponent getCity(GeocodingResult[] res) {
        return getComponent(res, AddressType.LOCALITY);
    }

    private static AddressComponent getPostalCode(GeocodingResult[] res) {
        return getComponent(res, AddressType.POSTAL_CODE);
    }

    private static AddressComponent getRegion(GeocodingResult[] res) {
        return getComponent(res, AddressType.ADMINISTRATIVE_AREA_LEVEL_1);
    }

    @Override
    protected void doStart() {
        context = endpoint.createGeoApiContext();
    }
}
