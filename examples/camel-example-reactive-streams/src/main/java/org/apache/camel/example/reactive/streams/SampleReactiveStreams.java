package org.apache.camel.example.reactive.streams;

import javax.annotation.PostConstruct;

import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component
public class SampleReactiveStreams {

    private static Logger LOG = LoggerFactory.getLogger(SampleReactiveStreams.class);

    @Autowired
    private CamelReactiveStreamsService camelStreams;

    @PostConstruct
    public void configure() {

        Publisher<Integer> numbers = camelStreams.getPublisher("numbers", Integer.class);
        Publisher<String> strings = camelStreams.getPublisher("strings", String.class);

        Flux.from(numbers)
                .zipWith(strings)
                .map(tuple -> "Seq: " + tuple.getT1() + " - " + tuple.getT2())
                .doOnNext(LOG::info)
                .subscribe();

    }

}
