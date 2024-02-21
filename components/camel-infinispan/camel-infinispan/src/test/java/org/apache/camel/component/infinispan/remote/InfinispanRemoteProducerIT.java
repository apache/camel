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
package org.apache.camel.component.infinispan.remote;

import java.util.function.BiFunction;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.InfinispanProducerTestSupport;
import org.infinispan.client.hotrod.ServerStatistics;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InfinispanRemoteProducerIT extends InfinispanRemoteTestSupport implements InfinispanProducerTestSupport {

    @BindToRegistry("mappingFunction")
    public BiFunction<String, String, String> mappingFunction() {
        return (k, v) -> v + "replay";
    }

    @Test
    public void statsOperation() {
        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, InfinispanProducerTestSupport.KEY_ONE)
                .withHeader(InfinispanConstants.VALUE, InfinispanProducerTestSupport.VALUE_ONE)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals(InfinispanProducerTestSupport.VALUE_ONE, getCache().get(InfinispanProducerTestSupport.KEY_ONE));

        fluentTemplate()
                .to("direct:start")
                .withHeader(InfinispanConstants.KEY, InfinispanProducerTestSupport.KEY_TWO)
                .withHeader(InfinispanConstants.VALUE, InfinispanProducerTestSupport.VALUE_TWO)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .send();

        assertEquals(InfinispanProducerTestSupport.VALUE_TWO, getCache().get(InfinispanProducerTestSupport.KEY_TWO));

        assertEquals(
                -1,
                fluentTemplate()
                        .to("direct:start")
                        .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.STATS)
                        .request(ServerStatistics.class)
                        .getIntStatistic(ServerStatistics.CURRENT_NR_OF_ENTRIES));
    }

    // *****************************
    //
    // *****************************

    @BeforeEach
    protected void beforeEach() {
        // cleanup the default test cache before each run
        getCache().clear();
    }

    @Override
    public BasicCache<Object, Object> getCache() {
        return super.getCache();
    }

    @Override
    public BasicCache<Object, Object> getCache(String name) {
        return super.getCache(name);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("infinispan:%s", getCacheName());
                from("direct:compute")
                        .toF("infinispan:%s?remappingFunction=#mappingFunction", getCacheName());
                from("direct:explicitput")
                        .toF("infinispan:%s?operation=PUT&key=a&value=3", getCacheName());
            }
        };
    }
}
