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
package org.apache.camel.component.weather;

import java.util.List;
import java.util.Scanner;

import org.apache.camel.component.weather.geolocation.FreeGeoIpGeoLocationProvider;
import org.apache.camel.component.weather.geolocation.GeoLocationProvider;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import static org.apache.camel.component.weather.WeatherLanguage.en;
import static org.apache.camel.component.weather.WeatherMode.JSON;
import static org.apache.camel.util.ObjectHelper.notNull;

@UriParams
public class WeatherConfiguration {

    @UriPath(description = "The name value is not used.")
    @Metadata(required = true)
    private String name;
    @UriParam(description = "The API key of the OpenWeatherMap account, sent as the APPID query parameter of every request.",
              label = "security", security = "secret")
    @Metadata(required = true)
    private String appid;
    @UriParam(description = "The OpenWeatherMap API to call. Station queries weather stations instead of cities, for ids, "
                            + "for lat and lon with cnt, and for a bounding box. When period is set, Hourly gets the 3-hour "
                            + "forecast and any other value, or none, the daily forecast; without period, Hourly and Daily get "
                            + "the current weather.")
    private WeatherApi weatherApi;
    @UriParam(description = "The city to get the weather for, by name and optionally a country code, for example London,uk. "
                            + "If empty or current, the latitude and longitude come from the geoLocationProvider. The lat and "
                            + "lon, zip and ids options take precedence over this option, and the CamelWeatherLocation header "
                            + "replaces it for the producer.",
              label = "filter")
    private String location = "";
    @UriParam(description = "The latitude of the location, used together with lon, and taking precedence over the zip, ids "
                            + "and location options. For a bounding box query (with rightLon and topLat) it is the bottom "
                            + "latitude. With cnt set, the query finds that many cities, or stations with weatherApi Station, "
                            + "around the point.",
              label = "filter")
    private String lat;
    @UriParam(description = "The longitude of the location, used together with lat. For a bounding box query (with rightLon "
                            + "and topLat) it is the left longitude.",
              label = "filter")
    private String lon;
    @UriParam(description = "The right longitude of a bounding box query, which also needs lat, lon, topLat and zoom. The "
                            + "query gets the weather of the cities in the box, or of the stations with weatherApi Station.",
              label = "filter")
    private String rightLon;
    @UriParam(description = "The top latitude of a bounding box query, which also needs lat, lon, rightLon and zoom.",
              label = "filter")
    private String topLat;
    @UriParam(description = "The map zoom level of a bounding box query, which also needs lat, lon, rightLon and topLat.",
              label = "filter")
    private Integer zoom;
    @UriParam(description = "The length of a forecast, sent as the cnt query parameter: the number of days of the daily "
                            + "forecast, or the number of 3-hour steps with weatherApi Hourly. Only the first number in the "
                            + "value is read, so 7 days is the same as 7, and a value with no number, or 0, is ignored. "
                            + "If empty, the current weather is queried instead of a forecast.")
    private String period = "";
    @UriParam(description = "The format of the response from OpenWeatherMap: JSON, XML or HTML. XML and HTML are sent as the "
                            + "mode query parameter.",
              defaultValue = "JSON")
    private WeatherMode mode = JSON;
    @UriParam(description = "The units of measurement in the response, for example of temperature and wind speed, sent as "
                            + "the units query parameter. If not set, OpenWeatherMap uses its standard units, with the "
                            + "temperature in Kelvin.")
    private WeatherUnits units;
    @UriParam(description = "The language of the weather descriptions in the response, sent as the lang query parameter.",
              defaultValue = "en")
    private WeatherLanguage language = en;
    @UriParam(description = "The name of the header to store the response in, instead of the message body. The producer then "
                            + "leaves the body as it is, and the consumer sends an exchange without a body.")
    private String headerName;
    @UriParam(description = "The zip code and the country code of the location, for example 94040,us. It is used when lat "
                            + "and lon are not set, and takes precedence over the ids and location options.",
              label = "filter")
    private String zip;
    @UriParam(description = "The ID of a city, or of a station with weatherApi Station, or a comma-separated list of city "
                            + "IDs to get the current weather of all of them in one request. It is used when lat, lon and zip "
                            + "are not set, and takes precedence over the location option.",
              label = "filter", javaType = "java.lang.String")
    private String ids;
    @UriParam(description = "The number of results, sent as the cnt query parameter, for example the number of cities or "
                            + "stations to find around lat and lon. The period option takes precedence over this option.",
              label = "filter")
    private Integer cnt;
    @UriParam(description = "The access key of the ipstack API, which the default geoLocationProvider uses. It is needed "
                            + "only when the location is looked up: location is empty or current, and none of lat and lon, zip "
                            + "or ids is set.",
              label = "security", security = "secret")
    @Metadata(required = true)
    private String geolocationAccessKey;
    @UriParam(description = "The IP address that the default geoLocationProvider looks up with the ipstack API to find the "
                            + "latitude and longitude of the location. It is needed only when the location is looked up, as "
                            + "for geolocationAccessKey.",
              label = "security")
    @Metadata(required = true)
    private String geolocationRequestHostIP;
    @UriParam(description = "To use a custom org.apache.hc.client5.http.impl.classic.CloseableHttpClient, for example one "
                            + "with an HTTP proxy, for the requests to OpenWeatherMap and to the default geoLocationProvider. "
                            + "By default each endpoint creates its own client. The endpoint closes the client when it stops, "
                            + "also a client that is set.",
              label = "advanced")
    private CloseableHttpClient httpClient = HttpClients.createDefault();
    @UriParam(description = "A custom org.apache.camel.component.weather.geolocation.GeoLocationProvider for the latitude "
                            + "and longitude of the location when location is empty or current, and none of lat and lon, zip "
                            + "or ids is set. The default looks up geolocationRequestHostIP with the ipstack API, and needs "
                            + "geolocationAccessKey.",
              label = "advanced")
    private GeoLocationProvider geoLocationProvider = new FreeGeoIpGeoLocationProvider(this);

    public WeatherConfiguration() {

    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        notNull(period, "period");
        int result = 0;
        try (Scanner scanner = new Scanner(period)) {
            result = scanner.useDelimiter("\\D+").nextInt();
        } catch (Exception e) {
            // ignore and fallback the period to be an empty string
        }
        if (result != 0) {
            this.period = Integer.toString(result);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WeatherMode getMode() {
        return mode;
    }

    public void setMode(WeatherMode mode) {
        this.mode = notNull(mode, "mode");
    }

    public WeatherUnits getUnits() {
        return units;
    }

    public void setUnits(WeatherUnits units) {
        this.units = notNull(units, "units");
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getAppid() {
        return appid;
    }

    public WeatherLanguage getLanguage() {
        return language;
    }

    public void setLanguage(WeatherLanguage language) {
        this.language = language;
    }

    public String getRightLon() {
        return rightLon;
    }

    public void setRightLon(String rightLon) {
        this.rightLon = rightLon;
    }

    public String getTopLat() {
        return topLat;
    }

    public void setTopLat(String topLat) {
        this.topLat = topLat;
    }

    public Integer getZoom() {
        return zoom;
    }

    public void setZoom(Integer zoom) {
        this.zoom = zoom;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getIds() {
        return ids;
    }

    public List<String> getIdsAsList() {
        if (ids != null) {
            return List.of(ids.split(","));
        } else {
            return null;
        }
    }

    public void setIds(String ids) {
        this.ids = ids;
    }

    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }

    public WeatherApi getWeatherApi() {
        return weatherApi;
    }

    public void setWeatherApi(WeatherApi weatherApi) {
        this.weatherApi = weatherApi;
    }

    public String getGeolocationAccessKey() {
        return geolocationAccessKey;
    }

    public void setGeolocationAccessKey(String geolocationAccessKey) {
        this.geolocationAccessKey = geolocationAccessKey;
    }

    public String getGeolocationRequestHostIP() {
        return geolocationRequestHostIP;
    }

    public void setGeolocationRequestHostIP(String geolocationRequestHostIP) {
        this.geolocationRequestHostIP = geolocationRequestHostIP;
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public GeoLocationProvider getGeoLocationProvider() {
        return geoLocationProvider;
    }

    public void setGeoLocationProvider(GeoLocationProvider geoLocationProvider) {
        this.geoLocationProvider = geoLocationProvider;
    }
}
