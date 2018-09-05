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
package org.apache.camel.example.cdi.xml;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.Body;
import org.apache.camel.CamelException;
import org.apache.camel.Handler;
import org.apache.camel.Processor;
import org.apache.camel.cdi.ImportResource;
import org.apache.camel.management.event.RouteStoppedEvent;

import static org.apache.camel.builder.Builder.simple;

/**
 * This example imports a Camel XML configuration file from the classpath using the
 * {@code ImportResource} annotation.<p>
 *
 * The imported Camel XML file configures a Camel context that references CDI beans
 * declared in this class.
 */
@Named("matrix")
@ImportResource("camel-context.xml")
public class Application {

    @Named
    @Produces
    Exception morpheus = new CamelException("All I'm offering is the truth!");

    @Named
    @Produces
    Processor tracer = exchange -> exchange.getIn()
        .setHeader("location", simple("exchangeProperty.CamelFailureRouteId"));

    void login(@Observes @Initialized(ApplicationScoped.class) Object event) {
        System.out.println(
              "████████╗██╗  ██╗███████╗    ███╗   ███╗ █████╗ ████████╗██████╗ ██╗██╗  ██╗\n"
            + "╚══██╔══╝██║  ██║██╔════╝    ████╗ ████║██╔══██╗╚══██╔══╝██╔══██╗██║╚██╗██╔╝\n"
            + "   ██║   ███████║█████╗      ██╔████╔██║███████║   ██║   ██████╔╝██║ ╚███╔╝ \n"
            + "   ██║   ██╔══██║██╔══╝      ██║╚██╔╝██║██╔══██║   ██║   ██╔══██╗██║ ██╔██╗ \n"
            + "   ██║   ██║  ██║███████╗    ██║ ╚═╝ ██║██║  ██║   ██║   ██║  ██║██║██╔╝ ██╗\n"
            + "   ╚═╝   ╚═╝  ╚═╝╚══════╝    ╚═╝     ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚═╝╚═╝  ╚═╝");
    }

    @Handler
    public String terminal(@Body String body) {
        return "Matrix » " + body;
    }

    void logout(@Observes @Named("terminal") RouteStoppedEvent event) {
        System.out.println(
              "                                     __    \n"
            + " _____         _                   _|  |   \n"
            + "|  |  |___ ___| |_ _ ___ ___ ___ _| |  |   \n"
            + "|  |  |   | . | | | | . | . | -_| . |__|   \n"
            + "|_____|_|_|  _|_|___|_  |_  |___|___|__|   \n"
            + "          |_|       |___|___|              ");
    }

    void shutdown(@Observes @Destroyed(ApplicationScoped.class) Object event) {
        System.out.println(
              " _____ _       _     _                     \n"
            + "|   __| |_ _ _| |_ _| |___ _ _ _ ___       \n"
            + "|__   |   | | |  _| . | . | | | |   |_ _ _ \n"
            + "|_____|_|_|___|_| |___|___|_____|_|_|_|_|_|");
    }
}