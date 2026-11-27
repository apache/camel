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

import org.apache.camel.spi.Metadata;

public final class GeoCoderConstants {

    @Metadata(description = "The address. The producer reads it as the address to look up, which replaces the address of"
                            + " the endpoint, and sets it to the formatted address of the first result.",
              javaType = "String")
    public static final String ADDRESS = "CamelGeoCoderAddress";
    @Metadata(description = "The latitude and longitude, separated by a comma. The producer reads it as the location to look"
                            + " up, which replaces the latlng of the endpoint and takes precedence over an address, and sets it"
                            + " to the location of the first result. For Nominatim, it sets a space after the comma.",
              javaType = "String")
    public static final String LATLNG = "CamelGeoCoderLatlng";
    @Metadata(description = "The latitude of the first result, with 8 decimals.", javaType = "String")
    public static final String LAT = "CamelGeoCoderLat";
    @Metadata(description = "The longitude of the first result, with 8 decimals.", javaType = "String")
    public static final String LNG = "CamelGeoCoderLng";
    @Metadata(description = "The status of the lookup: OK, or ZERO_RESULTS when no place is found. When Google rejects the"
                            + " request, it is REQUEST_DENIED, OVER_QUERY_LIMIT, OVER_DAILY_LIMIT, INVALID_REQUEST or"
                            + " UNKNOWN_ERROR, and for an error answer of Nominatim it is ERROR. The producer sets the other"
                            + " headers only when the status is OK, except that for the address current it sets"
                            + " CamelGeoCoderLatlng to the geolocated location also when the status is ZERO_RESULTS.",
              javaType = "org.apache.camel.component.geocoder.GeocoderStatus", required = true)
    public static final String STATUS = "CamelGeoCoderStatus";
    @Metadata(description = "The code of the first-level administrative area, such as a state. With Google, the short name"
                            + " of the administrative_area_level_1 address component, and with Nominatim the state_code of the"
                            + " address in upper case.",
              javaType = "String")
    public static final String REGION_CODE = "CamelGeoCoderRegionCode";
    @Metadata(description = "The name of the first-level administrative area, such as a state. With Google, the long name"
                            + " of the administrative_area_level_1 address component, and with Nominatim the state of the"
                            + " address.",
              javaType = "String")
    public static final String REGION_NAME = "CamelGeoCoderRegionName";
    @Metadata(description = "The name of the city. With Google, the long name of the locality address component, and with"
                            + " Nominatim the city of the address.",
              javaType = "String")
    public static final String CITY = "CamelGeoCoderCity";
    @Metadata(description = "The name of the country, such as France.", javaType = "String")
    public static final String COUNTRY_LONG = "CamelGeoCoderCountryLong";
    @Metadata(description = "The two-letter country code (ISO 3166-1 alpha-2) in upper case, such as FR.", javaType = "String")
    public static final String COUNTRY_SHORT = "CamelGeoCoderCountryShort";
    @Metadata(description = "The postal code.", javaType = "String")
    public static final String POSTAL_CODE = "CamelGeoCoderPostalCode";

    private GeoCoderConstants() {
    }

}
