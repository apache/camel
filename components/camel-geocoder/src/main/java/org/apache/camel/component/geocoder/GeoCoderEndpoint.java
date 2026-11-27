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

import java.net.InetSocketAddress;
import java.net.Proxy;

import com.google.maps.GeoApiContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.geocoder.http.AuthenticationMethod;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Look up the latitude and longitude of an address, or the address of a latitude and longitude, with Google or a
 * Nominatim server.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "geocoder", title = "Geocoder", syntax = "geocoder:address:latlng",
             producerOnly = true, category = { Category.API, Category.SEARCH }, headersClass = GeoCoderConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not read: the address or the latitude and longitude come from the"
                                                + " CamelGeoCoderAddress or CamelGeoCoderLatlng header, or else from the endpoint"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "type", value = "NOMINATIM", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "headersOnly", value = "true",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "With Google, the array of com.google.maps.model.GeocodingResult, empty for"
                                                 + " no results; unchanged when Google denies the request, with the status in"
                                                 + " the CamelGeoCoderStatus header. With Nominatim, its jsonv2 response as text:"
                                                 + " an array of one place for an address (empty for no results), an object"
                                                 + " for a latitude and longitude. The first result is also in the headers"))
public class GeoCoderEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "The address to look up the latitude and longitude of, such as Paris, France, written after"
                           + " address: in the URI. With Google, the address current looks up the location of the IP address"
                           + " that the request comes from. The CamelGeoCoderAddress header replaces this option.")
    private String address;
    @UriPath(description = "The latitude and longitude to look up the address of, written after latlng: in the URI as two"
                           + " decimal numbers separated by a comma, such as 48.8566,2.3522. The CamelGeoCoderLatlng header"
                           + " replaces this option. A latitude and longitude, from this option or the header, takes"
                           + " precedence over an address.")
    private String latlng;
    @UriParam(defaultValue = "en",
              description = "The language of the answer (such as the address, the country and the city), as a language code"
                            + " such as en, de or fr. It is sent as the accept-language parameter to Nominatim and as the language"
                            + " parameter to Google.")
    private String language = "en";
    @UriParam(label = "security", security = "secret",
              description = "The client ID of the Google Maps Platform Premium Plan. When it is set, the producer signs the"
                            + " requests to Google with it and clientKey instead of using apiKey. The geolocation of the"
                            + " address current does not accept a client ID.")
    private String clientId;
    @UriParam(label = "security", security = "secret",
              description = "The URL signing secret (cryptographic key) that belongs to clientId, with which the producer"
                            + " signs the requests to Google.")
    private String clientKey;
    @UriParam(label = "security", security = "secret",
              description = "The API key of the Google Maps Platform, which the producer uses when clientId is not set."
                            + " Google needs either this key or clientId and clientKey. It is not used for Nominatim.")
    private String apiKey;
    @UriParam(description = "The base URL of the Nominatim server, such as https://nominatim.openstreetmap.org, to which the"
                            + " producer adds /search or /reverse. Required when type is NOMINATIM, and not used for Google.",
              displayName = "Server URL")
    private String serverUrl;
    @UriParam(description = "Whether to leave the message body as it is and only set the headers. If false, the producer"
                            + " sets the body to the result: an array of com.google.maps.model.GeocodingResult for Google, or"
                            + " the JSON response of Nominatim as a String.")
    private boolean headersOnly;
    @UriParam(label = "proxy",
              description = "The host name of the HTTP proxy for the requests to Google. The proxy is used only when"
                            + " proxyPort is also set, and not for Nominatim.")
    private String proxyHost;
    @UriParam(label = "proxy",
              description = "The port of the HTTP proxy for the requests to Google. The proxy is used only when proxyHost is"
                            + " also set.")
    private Integer proxyPort;
    @UriParam(label = "proxy",
              description = "The authentication method for the proxy, Basic or Digest. When it is set, the producer"
                            + " authenticates to the proxy with proxyAuthUsername and proxyAuthPassword. Another value fails"
                            + " when the producer starts.")
    private String proxyAuthMethod;
    @UriParam(label = "proxy", description = "The user name for the proxy authentication, used when proxyAuthMethod is set.")
    private String proxyAuthUsername;
    @UriParam(label = "proxy", security = "secret",
              description = "The password for the proxy authentication, used when proxyAuthMethod is set.")
    private String proxyAuthPassword;
    @UriParam(label = "proxy",
              description = "The domain for NTLM proxy authentication. The component does not use this option, as"
                            + " proxyAuthMethod accepts only Basic and Digest.")
    private String proxyAuthDomain;
    @UriParam(label = "proxy",
              description = "The host for NTLM proxy authentication. The component does not use this option, as"
                            + " proxyAuthMethod accepts only Basic and Digest.")
    private String proxyAuthHost;
    @UriParam(displayName = "GeoCoding Type",
              description = "The geocoding service: GOOGLE for the Google Geocoding API, or NOMINATIM for the Nominatim"
                            + " server at serverUrl. If not set, Google is used.")
    private GeoCoderType type;

    public GeoCoderEndpoint() {
    }

    public GeoCoderEndpoint(String uri, GeoCoderComponent component) {
        super(uri, component);
    }

    @Override
    public String getServiceUrl() {
        return "https://www.googleapis.com";
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }

    @Override
    public Producer createProducer() throws Exception {
        switch (getType()) {
            case NOMINATIM:
                return new GeoCoderNominatimProducer(this);
            case GOOGLE:
            default:
                // default to Google for backwards compatibility
                return new GeoCoderGoogleProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from this component");
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLatlng() {
        return latlng;
    }

    public void setLatlng(String latlng) {
        this.latlng = latlng;
    }

    public boolean isHeadersOnly() {
        return headersOnly;
    }

    public void setHeadersOnly(boolean headersOnly) {
        this.headersOnly = headersOnly;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyAuthMethod() {
        return proxyAuthMethod;
    }

    public void setProxyAuthMethod(String proxyAuthMethod) {
        this.proxyAuthMethod = proxyAuthMethod;
    }

    public String getProxyAuthUsername() {
        return proxyAuthUsername;
    }

    public void setProxyAuthUsername(String proxyAuthUsername) {
        this.proxyAuthUsername = proxyAuthUsername;
    }

    public String getProxyAuthPassword() {
        return proxyAuthPassword;
    }

    public void setProxyAuthPassword(String proxyAuthPassword) {
        this.proxyAuthPassword = proxyAuthPassword;
    }

    public String getProxyAuthDomain() {
        return proxyAuthDomain;
    }

    public void setProxyAuthDomain(String proxyAuthDomain) {
        this.proxyAuthDomain = proxyAuthDomain;
    }

    public String getProxyAuthHost() {
        return proxyAuthHost;
    }

    public void setProxyAuthHost(String proxyAuthHost) {
        this.proxyAuthHost = proxyAuthHost;
    }

    public GeoCoderType getType() {
        if (type == null) {
            type = GeoCoderType.GOOGLE;
        }
        return type;
    }

    public void setType(GeoCoderType type) {
        this.type = type;
    }

    public void setType(String type) {
        this.type = GeoCoderType.fromValue(type);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * Specific Google required
     *
     * @return
     */
    protected GeoApiContext createGeoApiContext() {
        GeoCoderComponent geoCoderComponent = (GeoCoderComponent) this.getComponent();
        if (geoCoderComponent.getGeoApiContext() != null) {
            return geoCoderComponent.getGeoApiContext();
        }
        GeoApiContext.Builder builder = new GeoApiContext.Builder();
        if (clientId != null) {
            builder = builder.enterpriseCredentials(clientId, clientKey);
        } else {
            builder = builder.apiKey(getApiKey());
        }
        if (isProxyDefined()) {
            builder = builder.proxy(createProxy());
            if (isProxyAuthDefined()) {
                builder = configureProxyAuth(builder);
            }
        }
        return builder.build();
    }

    private GeoApiContext.Builder configureProxyAuth(GeoApiContext.Builder builder) {
        AuthenticationMethod auth = getCamelContext().getTypeConverter().convertTo(AuthenticationMethod.class, proxyAuthMethod);
        if (auth == AuthenticationMethod.Basic || auth == AuthenticationMethod.Digest) {
            builder = builder.proxyAuthentication(proxyAuthUsername, proxyAuthPassword);
        } else {
            throw new IllegalArgumentException("Unknown proxyAuthMethod " + proxyAuthMethod);
        }
        return builder;
    }

    private Proxy createProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    private boolean isProxyDefined() {
        return proxyHost != null && proxyPort != null;
    }

    private boolean isProxyAuthDefined() {
        return proxyAuthMethod != null;
    }
}
