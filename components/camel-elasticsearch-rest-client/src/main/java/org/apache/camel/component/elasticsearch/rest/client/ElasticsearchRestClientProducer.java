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
package org.apache.camel.component.elasticsearch.rest.client;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.sniff.Sniffer;

public class ElasticsearchRestClientProducer extends DefaultAsyncProducer {
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String POST = "POST";
    public static final String GET = "GET";

    private ElasticsearchRestClientEndpoint endpoint;
    private RestClient restClient;
    private boolean createdRestClient;

    public ElasticsearchRestClientProducer(ElasticsearchRestClientEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            doProcess(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
        return false;
    }

    public void doProcess(Exchange exchange, AsyncCallback callback) throws Exception {
        String indexName = this.endpoint.getIndexName();
        if (ObjectHelper.isEmpty(indexName)) {
            indexName = exchange.getMessage().getHeader(ElasticSearchRestClientConstant.INDEX_NAME, String.class);
            if (ObjectHelper.isEmpty(indexName)) {
                throw new IllegalArgumentException(
                        "Index Name is mandatory");
            }
        }

        Request request = generateRequest(exchange, indexName);
        performRequest(exchange, callback, request);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        restClient = this.endpoint.getRestClient();
        if (restClient == null) {
            restClient = createClient();
            createdRestClient = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (createdRestClient && restClient != null) {
            IOHelper.close(restClient);
            restClient = null;
        }
    }

    /**
     * Generate REST Request depending on content of Exchange
     */
    private Request generateRequest(Exchange exchange, String indexName) throws Exception {
        return switch (this.endpoint.getOperation()) {
            case CREATE_INDEX -> createIndexRequest(indexName, exchange);
            case DELETE_INDEX -> deleteIndexRequest(indexName);
            case INDEX_OR_UPDATE -> indexRequest(indexName, exchange);
            case GET_BY_ID -> getById(indexName, exchange);
            case SEARCH -> search(indexName, exchange);
            case DELETE -> delete(indexName, exchange);
        };
    }

    /**
     * Async request to Elasticsearch or equivalent Server
     */
    private void performRequest(
            Exchange exchange, AsyncCallback callback, Request request) {
        restClient.performRequestAsync(
                request,
                new ResponseListener() {
                    @Override
                    public void onSuccess(Response response) {
                        try {
                            // Get response
                            String responseBody = EntityUtils.toString(response.getEntity());
                            // Create a Json Object from the response
                            JsonObject jsonObject = convertHttpEntityToJsonObject(responseBody);
                            populateExchange(jsonObject);
                        } catch (Exception e) {
                            exchange.setException(e);
                        }

                        callback.done(false);
                    }

                    private JsonObject convertHttpEntityToJsonObject(String httpResponse) throws IOException {
                        // Jackson ObjectMapper
                        ObjectMapper objectMapper = new ObjectMapper();

                        // Convert JSON content to Map<String, Object>
                        Map<String, Object> map = objectMapper.readValue(httpResponse, new TypeReference<>() {
                        });
                        // convert to JsonObject
                        return new JsonObject(map);
                    }

                    /**
                     * Generate response Body of the Exchange, depending on operation Type
                     */
                    private void populateExchange(JsonObject doc) {
                        switch (endpoint.getOperation()) {
                            case INDEX_OR_UPDATE -> exchange.getMessage().setBody(extractID(doc));
                            case CREATE_INDEX, DELETE_INDEX -> exchange.getMessage().setBody(extractAck(doc));
                            case DELETE -> exchange.getMessage().setBody(extractDeleted(doc));
                            case GET_BY_ID -> exchange.getMessage().setBody(extractDocument(doc));
                            case SEARCH -> exchange.getMessage().setBody(extractSearch(doc));
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        exchange.setException(e);
                        callback.done(false);
                    }
                });
    }

    /***
     * Generate the Request for operation CREATE_INDEX
     */
    private Request createIndexRequest(String indexName, Exchange exchange) {
        var endpoint = String.format("/%s", indexName);
        var request = new Request(PUT, endpoint);
        var additionalParameters
                = exchange.getMessage().getHeader(ElasticSearchRestClientConstant.INDEX_SETTINGS, String.class);
        if (ObjectHelper.isNotEmpty(additionalParameters)) {
            request.setEntity(new NStringEntity(additionalParameters, ContentType.APPLICATION_JSON));
        }
        return request;
    }

    /***
     * Generate the Request for operation DELETE_INDEX
     */
    private Request deleteIndexRequest(String indexName) {
        var endpoint = String.format("/%s", indexName);
        return new Request(DELETE, endpoint);
    }

    /**
     * Generate the Request for operation INDEX_OR_UPDATE
     */
    private Request indexRequest(String indexName, Exchange exchange) throws InvalidPayloadException {
        var jsonBody = exchange.getMessage().getMandatoryBody(String.class);

        var endpoint = String.format("/%s/_doc", indexName);

        // Gets the id of the document if specified
        var id = exchange.getMessage().getHeader(ElasticSearchRestClientConstant.ID);
        if (ObjectHelper.isNotEmpty(id)) {
            endpoint = String.format("%s/%s", endpoint, id);
        }

        Request request = new Request(POST, endpoint);
        request.setEntity(new NStringEntity(jsonBody, ContentType.APPLICATION_JSON));

        return request;
    }

    /**
     * Generate the Request for operation GET_BY_ID
     */
    private Request getById(String indexName, Exchange exchange) {
        var id = exchange.getMessage().getBody(String.class);
        if (ObjectHelper.isEmpty(id)) {
            id = exchange.getMessage().getHeader(ElasticSearchRestClientConstant.ID, String.class);
            if (ObjectHelper.isEmpty(id)) {
                throw new IllegalArgumentException(
                        "id value is mandatory when performing GET_BY_ID operation");
            }
        }
        var endpoint = String.format("/%s/_doc/%s", indexName, id);
        return new Request(GET, endpoint);
    }

    /**
     * Generate the Request for DELETE
     */
    private Request delete(String indexName, Exchange exchange) {
        var id = exchange.getMessage().getBody(String.class);
        if (ObjectHelper.isEmpty(id)) {
            id = exchange.getMessage().getHeader(ElasticSearchRestClientConstant.ID, String.class);
            if (ObjectHelper.isEmpty(id)) {
                throw new IllegalArgumentException(
                        "id value is mandatory when performing DELETE operation");
            }
        }
        var endpoint = String.format("/%s/_doc/%s", indexName, id);
        return new Request(DELETE, endpoint);
    }

    /**
     * Generate the Request for operation SEARCH
     */
    private Request search(String indexName, Exchange exchange) {
        var endpoint = String.format("/%s/_search", indexName);
        // Create Request : at this stage this one get all results
        Request request = new Request(GET, endpoint);

        // if user has passed the Request in the header
        var advancedQuery = exchange.getMessage().getHeader(ElasticSearchRestClientConstant.SEARCH_QUERY, String.class);
        if (ObjectHelper.isNotEmpty(advancedQuery)) {
            request.setJsonEntity(advancedQuery);
        } else {
            // check if we have criterias in the body for simple matches, to create the query Json request
            var queryParameters = exchange.getMessage().getBody(Map.class);
            if (ObjectHelper.isNotEmpty(queryParameters)) {
                var jsonRequest = createQueryFromMap(queryParameters);
                request.setJsonEntity(jsonRequest);
            }
        }

        return request;
    }

    /**
     * creates an Elasticsearch Json Query based on matches
     */
    private String createQueryFromMap(Map<String, String> queryParameters) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode matchObject = objectMapper.createObjectNode();
        ArrayNode mustArray = objectMapper.createArrayNode();

        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
            ObjectNode fieldObject = objectMapper.createObjectNode();
            fieldObject.set("match", objectMapper.createObjectNode().put(entry.getKey(), entry.getValue()));
            mustArray.add(fieldObject);
        }

        matchObject.set("must", mustArray);

        ObjectNode boolObject = objectMapper.createObjectNode().set("bool", matchObject);
        ObjectNode queryObject = objectMapper.createObjectNode().set("query", boolObject);

        // Convert the JSON object to a string
        return queryObject.toPrettyString();
    }

    /***
     * Extract ACK value from Response Body from API server
     */
    boolean extractAck(JsonObject doc) {
        return doc.getBoolean("acknowledged");
    }

    /**
     * Extract Deleted from Response Body from API Server
     */
    boolean extractDeleted(JsonObject doc) {
        return "deleted".equals(doc.getString("result"));
    }

    /**
     * Extract ID value from Response Body from API server
     */
    String extractID(JsonObject doc) {
        return doc.getString("_id");
    }

    /**
     * Extract Document from response
     */
    String extractDocument(JsonObject doc) {
        // check if document exists
        Boolean found = doc.getBoolean("found");
        if (!found) {
            return null;
        }
        // if it exists extract document
        Map<String, Object> map = doc.getMap("_source");
        JsonObject jsonObject = new JsonObject(map);

        return jsonObject.toJson();
    }

    /**
     * Extract Document from response
     */
    String extractSearch(JsonObject doc) {
        Map<String, Object> hitsLevel1 = doc.getMap("hits");
        List<Map<String, Object>> hitsLevel2;
        hitsLevel2 = (List<Map<String, Object>>) hitsLevel1.get("hits");
        List<Object> extractedValues = hitsLevel2.stream()
                .map(map -> map.get("_source"))
                .collect(Collectors.toList());
        JsonArray response = new JsonArray(extractedValues);
        return response.toJson();
    }

    /**
     * Creates Rest Client from information in the Endpoint
     */
    private RestClient createClient() throws Exception {
        final RestClientBuilder builder = RestClient.builder(getHttpHosts());

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(this.endpoint.getConnectionTimeout()).setSocketTimeout(this.endpoint.getSocketTimeout()));

        if (this.endpoint.getUser() != null && this.endpoint.getPassword() != null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(this.endpoint.getUser(), this.endpoint.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                if (this.endpoint.getCertificatePath() != null) {
                    httpClientBuilder.setSSLContext(createSslContextFromCa());
                }
                return httpClientBuilder;
            });
        }
        final RestClient restClient = builder.build();

        // initiate Sniffer
        if (this.endpoint.isEnableSniffer()) {
            Sniffer.builder(restClient)
                    .setSniffIntervalMillis(this.endpoint.getSnifferInterval())
                    .setSniffAfterFailureDelayMillis(this.endpoint.getSniffAfterFailureDelay())
                    .build();
        }
        return restClient;
    }

    private HttpHost[] getHttpHosts() {
        if (ObjectHelper.isEmpty(this.endpoint.getHostAddressesList())) {
            throw new IllegalArgumentException(
                    "RestClient or HostAddressesList is mandatory");
        }

        String[] hostAdresses = this.endpoint.getHostAddressesList().split(",");
        // Create an array of HttpHost instances
        HttpHost[] httpHostArray = Arrays.stream(hostAdresses)
                .map(address -> HttpHost.create(address.trim()))
                .toArray(HttpHost[]::new);
        return httpHostArray;
    }

    /**
     * A SSL context based on the self-signed CA, so that using this SSL Context allows to connect to the Elasticsearch
     * service
     *
     * @return a customized SSL Context
     */
    private SSLContext createSslContextFromCa() {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            InputStream resolveMandatoryResourceAsInputStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getEndpoint().getCamelContext(),
                            this.endpoint.getCertificatePath());
            Certificate trustedCa = factory.generateCertificate(resolveMandatoryResourceAsInputStream);
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", trustedCa);

            final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            TrustManagerFactory trustManagerFactory
                    = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
