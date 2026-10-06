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
package org.apache.camel.component.couchbase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.view.ViewOptions;
import com.couchbase.client.java.view.ViewResult;
import com.couchbase.client.java.view.ViewRow;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The message body the consumer creates for a row, with and without {@code fullDocument}.
 * <p/>
 * The scheduler of the consumer is not started; the test calls {@code poll()} itself against a mocked cluster.
 */
class CouchbaseConsumerBodyTest {

    private static final String URI = "couchbase:http://localhost:8091?bucket=bucket&username=user&password=secret"
                                      + "&startScheduler=false";
    private static final String VIEW = "&useView=true&designDocumentName=dd&viewName=view";

    private DefaultCamelContext context;
    private MockedStatic<Cluster> clusters;
    private Bucket bucket;
    private Scope scope;
    private Collection collection;
    private final List<Object> bodies = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bucket = mock(Bucket.class);
        scope = mock(Scope.class);
        collection = mock(Collection.class);
        when(bucket.defaultScope()).thenReturn(scope);
        when(bucket.defaultCollection()).thenReturn(collection);
        Cluster cluster = mock(Cluster.class);
        when(cluster.bucket(anyString())).thenReturn(bucket);
        clusters = mockStatic(Cluster.class);
        clusters.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(cluster);
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        clusters.close();
    }

    private CouchbaseConsumer startRoute(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("couchbase").process(exchange -> bodies.add(exchange.getMessage().getBody()));
            }
        });
        context.start();
        return (CouchbaseConsumer) context.getRoute("couchbase").getConsumer();
    }

    @SuppressWarnings("deprecation")
    private void viewReturns(ViewRow... rows) {
        ViewResult result = mock(ViewResult.class);
        when(result.rows()).thenReturn(List.of(rows));
        when(bucket.viewQuery(anyString(), anyString(), any(ViewOptions.class))).thenReturn(result);
    }

    /** A view row as the SDK returns it: the value is an {@code Optional}, empty when the view emitted null. */
    private static ViewRow viewRow(String id, Object value) {
        ViewRow row = mock(ViewRow.class);
        when(row.id()).thenReturn(Optional.of(id));
        when(row.valueAs(Object.class)).thenReturn(Optional.ofNullable(value));
        when(row.keyAs(String.class)).thenReturn(Optional.of(id));
        return row;
    }

    @Test
    void theValueEmittedByTheViewIsTheBodyWithoutFullDocument() throws Exception {
        Map<String, Object> value = Map.of("name", "Alice");
        viewReturns(viewRow("doc-1", value), viewRow("doc-2", "Bob"));
        CouchbaseConsumer consumer = startRoute(URI + VIEW + "&fullDocument=false");

        assertEquals(2, consumer.poll());

        assertEquals(List.of(value, "Bob"), bodies, "the body must be the value of the view row, not an Optional");
    }

    @Test
    void theBodyIsNullWhenTheViewEmitsNoValue() throws Exception {
        viewReturns(viewRow("doc-1", null));
        CouchbaseConsumer consumer = startRoute(URI + VIEW + "&fullDocument=false");

        assertEquals(1, consumer.poll());

        assertEquals(1, bodies.size());
        assertNull(bodies.get(0), "the body must be null when the view emitted null, not an empty Optional");
    }
}
