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
package org.apache.camel.cdi.test;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.bean.OtherCamelRoute;
import org.apache.camel.cdi.bean.SimpleCamelRoute;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.RouteAddedEvent;
import org.apache.camel.spi.CamelEvent.RouteRemovedEvent;
import org.apache.camel.spi.CamelEvent.RouteStartedEvent;
import org.apache.camel.spi.CamelEvent.RouteStoppedEvent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class CamelRouteEventNotifierTest {

    @Produces
    @Named("all")
    @ApplicationScoped
    private List<CamelEvent> allFiredEvents = new ArrayList<>();

    @Produces
    @Named("simple")
    @ApplicationScoped
    private List<CamelEvent> simpleFiredEvents = new ArrayList<>();

    @Produces
    @Named("other")
    @ApplicationScoped
    private List<CamelEvent> otherFiredEvents = new ArrayList<>();

    private void onRouteAddedEventEventAll(@Observes RouteAddedEvent event,
                                           @Named("all") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteAddedEventEventSimple(@Observes @Named("simple") RouteAddedEvent event,
                                              @Named("simple") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteAddedEventEventOther(@Observes @Named("other") RouteAddedEvent event,
                                             @Named("other") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteStartedEventEventAll(@Observes RouteStartedEvent event,
                                             @Named("all") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteStartedEventEventSimple(@Observes @Named("simple") RouteStartedEvent event,
                                                @Named("simple") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteStartedEventEventOther(@Observes @Named("other") RouteStartedEvent event,
                                               @Named("other") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteStoppedEventEventAll(@Observes RouteStoppedEvent event,
                                             @Named("all") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteStoppedEventEventSimple(@Observes @Named("simple") RouteStoppedEvent event,
                                                @Named("simple") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteStoppedEventEventOther(@Observes @Named("other") RouteStoppedEvent event,
                                               @Named("other") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteRemovedEventEventAll(@Observes RouteRemovedEvent event,
                                             @Named("all") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteRemovedEventEventSimple(@Observes @Named("simple") RouteRemovedEvent event,
                                                @Named("simple") List<CamelEvent> events) {
        events.add(event);
    }

    private void onRouteRemovedEventEventOther(@Observes @Named("other") RouteRemovedEvent event,
                                               @Named("other") List<CamelEvent> events) {
        events.add(event);
    }

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(SimpleCamelRoute.class, OtherCamelRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    @InSequence(1)
    public void startedCamelContext(@Named("all") List<CamelEvent> all,
                                    @Named("simple") List<CamelEvent> simple,
                                    @Named("other") List<CamelEvent> other) {
        assertThat("Events fired are incorrect!", all,
            contains(
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other")))))
            )
        );
        assertThat("Events fired are incorrect!", simple,
            contains(
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple")))))
            )
        );
        assertThat("Events fired are incorrect!", other,
            contains(
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other")))))
            )
        );
    }

    @Test
    @InSequence(3)
    public void stopCamelContext(CamelContext context,
                                 @Named("all") List<CamelEvent> all,
                                 @Named("simple") List<CamelEvent> simple,
                                 @Named("other") List<CamelEvent> other) throws Exception {
        context.stop();

        assertThat("Events fired are incorrect!", all,
            contains(
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStoppedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteRemovedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStoppedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteRemovedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple")))))
            )
        );
        assertThat("Events fired are incorrect!", simple,
            contains(
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteStoppedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple"))))),
                both(
                    instanceOf(RouteRemovedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("simple")))))
            )
        );
        assertThat("Events fired are incorrect!", other,
            contains(
                both(
                    instanceOf(RouteAddedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStartedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteStoppedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other"))))),
                both(
                    instanceOf(RouteRemovedEvent.class))
                    .and(hasProperty("route", hasProperty("id", is(equalTo("other")))))
            )
        );
    }
}
