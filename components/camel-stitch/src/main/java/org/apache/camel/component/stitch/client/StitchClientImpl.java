package org.apache.camel.component.stitch.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class StitchClientImpl implements StitchClient{

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
        HttpClient client2 = httpClient.headers(h -> {
           h.set(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
           h.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        });

        //httpClient.wiretap(true);

        System.out.println(requestBody.toJson());

        String res = client2.baseUrl(baseUrl)
                .post()
                .uri(BATCH_API_RESOURCE_URL)
                .send(ByteBufFlux.fromString(Mono.just(requestBody.toJson())))
                .responseSingle((httpClientResponse, byteBufFlux) -> {
                    System.out.println(httpClientResponse.responseHeaders());
                    System.out.println(httpClientResponse.status());
                    return byteBufFlux.asString();
                })
                .block();

        System.out.println(res);

        return Mono.empty();
    }
}
