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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import org.apache.camel.component.weather.geolocation.FreeGeoIpGeoLocationProvider;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ObjectHelper;
import org.apache.http.conn.HttpClientConnectionManager;


import static org.apache.camel.component.weather.WeatherLanguage.en;
import static org.apache.camel.component.weather.WeatherMode.JSON;
import static org.apache.camel.util.ObjectHelper.notNull;

@UriParams
public class WeatherConfiguration {

    private final WeatherComponent component;
    private final WeatherQuery weatherQuery;

    @UriPath(description = "The name value is not used.")
    @Metadata(required = true)
    private String name;
    @UriParam
    @Metadata(required = true)
    private String appid;
    @UriParam
    private WeatherApi weatherApi;
    @UriParam(label = "filter")
    private String location = "";
    @UriParam(label = "filter")
    private String lat;
    @UriParam(label = "filter")
    private String lon;
    @UriParam(label = "filter")
    private String rightLon;
    @UriParam(label = "filter")
    private String topLat;
    @UriParam(label = "filter")
    private Integer zoom;
    @UriParam
    private String period = "";
    @UriParam(defaultValue = "JSON")
    private WeatherMode mode = JSON;
    @UriParam
    private WeatherUnits units;
    @UriParam(defaultValue = "en")
    private WeatherLanguage language = en;
    @UriParam
    private String headerName;
    @UriParam(label = "filter")
    private String zip;
    @UriParam(label = "filter", javaType = "java.lang.String")
    private List<String> ids;
    @UriParam(label = "filter")
    private Integer cnt;
    @UriParam(label = "security")
    @Metadata(required = true)
    private String geolocationAccessKey;
    @UriParam(label = "security")
    @Metadata(required = true)
    private String geolocationRequestHostIP;

    public WeatherConfiguration(WeatherComponent component) {
        this.component = notNull(component, "component");
        weatherQuery = new WeatherQuery(this);
        FreeGeoIpGeoLocationProvider geoLocationProvider = new FreeGeoIpGeoLocationProvider(component, geolocationAccessKey);
        weatherQuery.setGeoLocationProvider(geoLocationProvider);
    }

    public String getPeriod() {
        return period;
    }

    /**
     * If null, the current weather will be returned, else use values of 5, 7,
     * 14 days. Only the numeric value for the forecast period is actually
     * parsed, so spelling, capitalisation of the time period is up to you (its
     * ignored)
     */
    public void setPeriod(String period) {
        notNull(period, "period");
        int result = 0;
        try {
            result = new Scanner(period).useDelimiter("\\D+").nextInt();
        } catch (Exception e) {
            // ignore and fallback the period to be an empty string
        }
        if (result != 0) {
            this.period = "" + result;
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

    /**
     * The output format of the weather data.
     */
    public void setMode(WeatherMode mode) {
        this.mode = notNull(mode, "mode");
    }

    public WeatherUnits getUnits() {
        return units;
    }

    /**
     * The units for temperature measurement.
     */
    public void setUnits(WeatherUnits units) {
        this.units = notNull(units, "units");
    }

    public String getLocation() {
        return location;
    }

    /**
     * If null Camel will try and determine your current location using the
     * geolocation of your ip address, else specify the city,country. For well
     * known city names, Open Weather Map will determine the best fit, but
     * multiple results may be returned. Hence specifying and country as well
     * will return more accurate data. If you specify "current" as the location
     * then the component will try to get the current latitude and longitude and
     * use that to get the weather details. You can use lat and lon options
     * instead of location.
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * To store the weather result in this header instead of the message body.
     * This is useable if you want to keep current message body as-is.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getLat() {
        return lat;
    }

    /**
     * Latitude of location. You can use lat and lon options instead of
     * location. For boxed queries this is the bottom latitude.
     */
    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    /**
     * Longitude of location. You can use lat and lon options instead of
     * location. For boxed queries this is the left longtitude.
     */
    public void setLon(String lon) {
        this.lon = lon;
    }

    /**
     * APPID ID used to authenticate the user connected to the API Server
     */
    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getAppid() {
        return appid;
    }

    String getQuery() throws Exception {
        return weatherQuery.getQuery();
    }

    String getQuery(String location) throws Exception {
        return weatherQuery.getQuery(location);
    }

    public WeatherLanguage getLanguage() {
        return language;
    }

    /**
     * Language of the response.
     */
    public void setLanguage(WeatherLanguage language) {
        this.language = language;
    }

    public String getRightLon() {
        return rightLon;
    }

    /**
     * For boxed queries this is the right longtitude. Needs to be used in
     * combination with topLat and zoom.
     */
    public void setRightLon(String rightLon) {
        this.rightLon = rightLon;
    }

    public String getTopLat() {
        return topLat;
    }

    /**
     * For boxed queries this is the top latitude. Needs to be used in
     * combination with rightLon and zoom.
     */
    public void setTopLat(String topLat) {
        this.topLat = topLat;
    }

    public Integer getZoom() {
        return zoom;
    }

    /**
     * For boxed queries this is the zoom. Needs to be used in combination with
     * rightLon and topLat.
     */
    public void setZoom(Integer zoom) {
        this.zoom = zoom;
    }

    public String getZip() {
        return zip;
    }

    /**
     * Zip-code, e.g. 94040,us
     */
    public void setZip(String zip) {
        this.zip = zip;
    }

    public List<String> getIds() {
        return ids;
    }

    /**
     * List of id's of city/stations. You can separate multiple ids by comma.
     */
    public void setIds(String id) {
        if (ids == null) {
            ids = new ArrayList<>();
        }
        Iterator<?> it = ObjectHelper.createIterator(id);
        while (it.hasNext()) {
            String myId = (String)it.next();
            ids.add(myId);
        }
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public Integer getCnt() {
        return cnt;
    }

    /**
     * Number of results to be found
     */
    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }

    public WeatherApi getWeatherApi() {
        return weatherApi;
    }

    /**
     * The API to be use (current, forecast/3 hour, forecast daily, station)
     */
    public void setWeatherApi(WeatherApi weatherApi) {
        this.weatherApi = weatherApi;
    }

    public String getGeolocationAccessKey() {
        return geolocationAccessKey;
    }

    /**
     * The geolocation service now needs an accessKey to be used
     */
    public void setGeolocationAccessKey(String geolocationAccessKey) {
        this.geolocationAccessKey = geolocationAccessKey;
    }

    public String getGeolocationRequestHostIP() {
        return geolocationRequestHostIP;
    }

    /**
     * The geolocation service now needs to specify the IP associated to the
     * accessKey you're using
     */
    public void setGeolocationRequestHostIP(String geolocationRequestHostIP) {
        this.geolocationRequestHostIP = geolocationRequestHostIP;
    }
}
