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
package org.apache.camel.component.leveldb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.component.leveldb.serializer.DefaultLevelDBSerializer;
import org.apache.camel.component.leveldb.serializer.JacksonLevelDBSerializer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.params.Parameter;
import org.apache.camel.test.junit5.params.Parameterized;
import org.apache.camel.test.junit5.params.Parameters;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs({ OS.AIX, OS.OTHER })
@Parameterized
public abstract class LevelDBTestSupport extends CamelTestSupport {

    @Parameter
    SerializerType serializerType;

    private LevelDBAggregationRepository repo;

    public enum SerializerType {
        JACKSON,
        JAVA_SERIALIZATION,
    }

    @Parameters
    public static Collection<Object[]> serializers() {
        Object[][] serializers = {
                { SerializerType.JAVA_SERIALIZATION },
                { SerializerType.JACKSON } };
        return Arrays.asList(serializers);
    }

    public SerializerType getSerializerType() {
        return serializerType;
    }

    public LevelDBSerializer getSerializer() {
        switch (serializerType) {
            case JACKSON:
                return new JacksonLevelDBSerializer();
            default:
                return new DefaultLevelDBSerializer();
        }
    }

    LevelDBAggregationRepository getRepo() {
        if (repo == null) {
            repo = createRepo();
        }

        return repo;
    }

    LevelDBAggregationRepository createRepo() {
        repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");
        repo.setSerializer(getSerializer());

        return repo;
    }

    static class StringAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }

    static class ByteAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                outputStream.write(oldExchange.getIn().getBody(byte[].class));
                outputStream.write(newExchange.getIn().getBody(byte[].class));

                oldExchange.getIn().setBody(outputStream.toByteArray());
            } catch (IOException e) {
                //ignore
            }

            return oldExchange;

        }
    }

    public static class IntegerAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            Integer body1 = oldExchange.getIn().getBody(Integer.class);
            Integer body2 = newExchange.getIn().getBody(Integer.class);
            int sum = body1 + body2;

            oldExchange.getIn().setBody(sum);
            return oldExchange;
        }
    }
}
