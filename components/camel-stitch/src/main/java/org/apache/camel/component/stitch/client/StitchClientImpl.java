package org.apache.camel.component.stitch.client;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;

public class StitchClientImpl implements StitchClient {

    private static final String BATCH_API_RESOURCE_URL = "/v2/import/batch";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String token;

    public StitchClientImpl(HttpClient httpClient, String baseUrl, String token) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override
    public Mono<StitchResponse> batch(final StitchRequestBody requestBody) throws JsonProcessingException {
        String res = httpClient
                .headers(applyHeaders())
                .baseUrl(baseUrl)
                .post()
                .uri(BATCH_API_RESOURCE_URL)
                .send(convertMapToByteBuf(requestBody.toMap()))
                .responseSingle((httpClientResponse, byteBufFlux) -> {
                    //System.out.println(httpClientResponse.responseHeaders().entries());
                    System.out.println(httpClientResponse.status().code());
                    httpClientResponse.responseHeaders().forEach(stringStringEntry -> {
                        System.out.println(stringStringEntry);
                    });
                    return byteBufFlux.asString();
                })
                .block();

        System.out.println(res);
        System.out.println(new ObjectMapper().readValue(res, Map.class));

        return Mono.empty();
    }

    private Mono<StitchResponse> sendBatch(final ByteBufFlux bodyAsByte) {
        httpClient
                .headers(applyHeaders())
                .baseUrl(baseUrl)
                .post()
                .uri(BATCH_API_RESOURCE_URL)
                .send(bodyAsByte)
                .responseSingle((httpClientResponse, byteBufFlux) -> {
                    System.out.println(httpClientResponse.responseHeaders());
                    System.out.println(httpClientResponse.status());

                    return byteBufFlux.asString();
                })
                .block();

        return Mono.empty();
    }

    private Consumer<? super HttpHeaders> applyHeaders() {
        return h -> {
            h.set(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
            h.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        };
    }

    private ByteBufFlux convertMapToByteBuf(final Map<String, Object> bodyAsMap) {
        return ByteBufFlux.fromString(Mono.just(convertBodyToJson(bodyAsMap)));
    }

    private String convertBodyToJson(final Map<String, Object> bodyAsMap) {
        try {
            return new ObjectMapper().writeValueAsString(bodyAsMap);
        }
        catch (IOException e) {
            throw new RuntimeException("Error occurred writing data map to JSON.", e);
        }
    }
}
