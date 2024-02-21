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
package org.apache.camel.component.ignite;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.EventType;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ignite.cache.IgniteCacheComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheEntryEventSerializableFilter;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IgniteCacheContinuousQueryTest extends AbstractIgniteTest implements Serializable {

    private static final long serialVersionUID = 1L;

    @BindToRegistry("query1")
    private ScanQuery<Integer, Person> scanQuery1 = new ScanQuery<>(new IgniteBiPredicate<Integer, Person>() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean apply(Integer key, Person person) {
            return person.getId() > 50;
        }
    });

    @BindToRegistry("remoteFilter1")
    private CacheEntryEventSerializableFilter<Integer, Person> remoteFilter
            = new CacheEntryEventSerializableFilter<Integer, IgniteCacheContinuousQueryTest.Person>() {
                private static final long serialVersionUID = 5624973479995548199L;

                @Override
                public boolean evaluate(CacheEntryEvent<? extends Integer, ? extends Person> event)
                        throws CacheEntryListenerException {
                    return event.getValue().getId() > 150;
                }
            };

    @Override
    protected String getScheme() {
        return "ignite-cache";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteCacheComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testContinuousQueryDoNotFireExistingEntries() throws Exception {
        context.getRouteController().startRoute("continuousQuery");

        getMockEndpoint("mock:test1").expectedMessageCount(100);

        Map<Integer, Person> persons = createPersons(1, 100);
        IgniteCache<Integer, Person> cache = ignite().getOrCreateCache("testcontinuous1");
        cache.putAll(persons);

        MockEndpoint.assertIsSatisfied(context);

        for (Exchange exchange : getMockEndpoint("mock:test1").getExchanges()) {
            Assertions.assertThat(exchange.getIn().getHeader(IgniteConstants.IGNITE_CACHE_NAME)).isEqualTo("testcontinuous1");
            Assertions.assertThat(exchange.getIn().getHeader(IgniteConstants.IGNITE_CACHE_EVENT_TYPE))
                    .isEqualTo(EventType.CREATED);
            Assertions.assertThat(exchange.getIn().getHeader(IgniteConstants.IGNITE_CACHE_KEY)).isIn(persons.keySet());
            Assertions.assertThat(exchange.getIn().getBody()).isIn(persons.values());
        }
    }

    @Test
    public void testContinuousQueryFireExistingEntriesWithQuery() throws Exception {
        getMockEndpoint("mock:test2").expectedMessageCount(50);

        Map<Integer, Person> persons = createPersons(1, 100);
        IgniteCache<Integer, Person> cache = ignite().getOrCreateCache("testcontinuous1");
        cache.putAll(persons);

        context.getRouteController().startRoute("continuousQuery.fireExistingEntries");

        MockEndpoint.assertIsSatisfied(context);

        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:test2").expectedMessageCount(100);

        persons = createPersons(101, 100);
        cache.putAll(persons);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testContinuousQueryFireExistingEntriesWithQueryAndRemoteFilter() throws Exception {
        getMockEndpoint("mock:test3").expectedMessageCount(50);

        Map<Integer, Person> persons = createPersons(1, 100);
        IgniteCache<Integer, Person> cache = ignite().getOrCreateCache("testcontinuous1");
        cache.putAll(persons);

        context.getRouteController().startRoute("remoteFilter");

        MockEndpoint.assertIsSatisfied(context);

        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:test3").expectedMessageCount(50);

        persons = createPersons(101, 100);
        cache.putAll(persons);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testContinuousQueryGroupedUpdates() throws Exception {
        // One hundred Iterables of 1 item each.
        getMockEndpoint("mock:test4").expectedMessageCount(100);

        context.getRouteController().startRoute("groupedUpdate");

        Map<Integer, Person> persons = createPersons(1, 100);
        IgniteCache<Integer, Person> cache = ignite().getOrCreateCache("testcontinuous1");
        cache.putAll(persons);

        MockEndpoint.assertIsSatisfied(context);

        for (Exchange exchange : getMockEndpoint("mock:test4").getExchanges()) {
            Assertions.assertThat(exchange.getIn().getHeader(IgniteConstants.IGNITE_CACHE_NAME)).isEqualTo("testcontinuous1");
            Assertions.assertThat(exchange.getIn().getBody()).isInstanceOf(Iterable.class);
            Assertions.assertThat(Iterators.size(exchange.getIn().getBody(Iterable.class).iterator())).isEqualTo(1);
        }

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ignite-cache:testcontinuous1?query=#query1").routeId("continuousQuery").noAutoStartup().to("mock:test1");

                from("ignite-cache:testcontinuous1?query=#query1&fireExistingQueryResults=true")
                        .routeId("continuousQuery.fireExistingEntries").noAutoStartup().to("mock:test2");

                from("ignite-cache:testcontinuous1?query=#query1&remoteFilter=#remoteFilter1&fireExistingQueryResults=true")
                        .routeId("remoteFilter").noAutoStartup()
                        .to("mock:test3");

                from("ignite-cache:testcontinuous1?pageSize=10&oneExchangePerUpdate=false").routeId("groupedUpdate")
                        .noAutoStartup().to("mock:test4");

            }
        };
    }

    private Map<Integer, Person> createPersons(int from, int count) {
        Map<Integer, Person> answer = Maps.newHashMap();
        int max = from + count;
        for (int i = from; i < max; i++) {
            answer.put(i, Person.create(i, "name" + i, "surname" + i));
        }
        return answer;
    }

    @AfterEach
    public void deleteCaches() {
        for (String cacheName : ImmutableSet.<String> of("testcontinuous1", "testcontinuous2", "testcontinuous3")) {
            Ignite ignite = ignite();
            if (ignite != null) {
                IgniteCache<?, ?> cache = ignite.cache(cacheName);
                if (cache != null) {
                    cache.clear();
                }
            }
        }
    }

    @AfterEach
    public void stopAllRoutes() throws Exception {
        for (Route route : context.getRoutes()) {
            if (context.getRouteController().getRouteStatus(route.getId()) != ServiceStatus.Started) {
                return;
            }
            context.getRouteController().stopRoute(route.getId());
        }

        MockEndpoint.resetMocks(context);
    }

    public static class Person implements Serializable {
        private static final long serialVersionUID = -6582521698437964648L;

        private Integer id;
        private String name;
        private String surname;

        public static Person create(Integer id, String name, String surname) {
            Person p = new Person();
            p.setId(id);
            p.setName(name);
            p.setSurname(surname);
            return p;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSurname() {
            return surname;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((surname == null) ? 0 : surname.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Person)) {
                return false;
            }

            if (this == obj) {
                return true;
            }

            Person other = (Person) obj;
            return Objects.equals(this.id, other.id) && Objects.equals(this.name, other.name)
                    && Objects.equals(this.surname, other.surname);
        }

    }

}
