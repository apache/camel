package org.apache.camel.component.stitch.client;

import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import reactor.core.publisher.Mono;

public class StitchClientImpl implements StitchClient{

    @Override
    public Mono<StitchResponse> batch(StitchMessage message) {
        return Mono.empty();
    }
}
