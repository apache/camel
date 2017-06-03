/**
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
package org.apache.camel.component.reactive.streams.platforms;

import java.util.function.Consumer;

import io.reactivex.Flowable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;


public class RxJava2PlatformTest extends AbstractPlatformTestSupport {

    @Override
    protected void changeSign(Publisher<Integer> data, Consumer<Integer> consume) {
        Flowable.fromPublisher(data)
                .map(i -> -i)
                .doOnNext(consume::accept)
                .subscribe();
    }

    @Override
    protected void changeSign(Iterable<Integer> data, Subscriber<Integer> camel) {
        Flowable.fromIterable(data)
                .map(i -> -i)
                .subscribe(camel);
    }
}
