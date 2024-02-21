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

    @Metadata(description = "The formatted address", javaType = "String")
    public static final String ADDRESS = "CamelGeoCoderAddress";
    @Metadata(description = "The latitude and longitude of the location. Separated by comma.", javaType = "String")
    public static final String LATLNG = "CamelGeoCoderLatlng";
    @Metadata(description = "The latitude of the location.", javaType = "String")
    public static final String LAT = "CamelGeoCoderLat";
    @Metadata(description = "The longitude of the location.", javaType = "String")
    public static final String LNG = "CamelGeoCoderLng";
    @Metadata(description = "Status code from the geocoder library. If status is\n" +
                            "`GeocoderStatus.OK` then additional headers is enriched",
              javaType = "org.apache.camel.component.geocoder.GeocoderStatus", required = true)
    public static final String STATUS = "CamelGeoCoderStatus";
    @Metadata(description = "The region code.", javaType = "String")
    public static final String REGION_CODE = "CamelGeoCoderRegionCode";
    @Metadata(description = "The region name.", javaType = "String")
    public static final String REGION_NAME = "CamelGeoCoderRegionName";
    @Metadata(description = "The city long name.", javaType = "String")
    public static final String CITY = "CamelGeoCoderCity";
    @Metadata(description = "The country long name.", javaType = "String")
    public static final String COUNTRY_LONG = "CamelGeoCoderCountryLong";
    @Metadata(description = "The country short name.", javaType = "String")
    public static final String COUNTRY_SHORT = "CamelGeoCoderCountryShort";
    @Metadata(description = "The postal code.", javaType = "String")
    public static final String POSTAL_CODE = "CamelGeoCoderPostalCode";

    private GeoCoderConstants() {
    }

}
