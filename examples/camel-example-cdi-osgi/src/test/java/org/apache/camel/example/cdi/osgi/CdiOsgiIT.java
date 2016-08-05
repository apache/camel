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
package org.apache.camel.example.cdi.osgi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.core.TinyBundles;

import static org.apache.camel.example.cdi.osgi.PaxExamOptions.ACTIVEMQ;
import static org.apache.camel.example.cdi.osgi.PaxExamOptions.CAMEL_CDI;
import static org.apache.camel.example.cdi.osgi.PaxExamOptions.CAMEL_COMMANDS;
import static org.apache.camel.example.cdi.osgi.PaxExamOptions.CAMEL_SJMS;
import static org.apache.camel.example.cdi.osgi.PaxExamOptions.KARAF;
import static org.apache.camel.example.cdi.osgi.PaxExamOptions.PAX_CDI_IMPL;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CdiOsgiIT {

    @Inject
    private CamelContext context;

    @Inject
    private SessionFactory sessionFactory;

    @Configuration
    public Option[] config() throws IOException {
        return options(
            KARAF.option(),
            CAMEL_COMMANDS.option(),
            PAX_CDI_IMPL.option(),
            CAMEL_CDI.option(),
            CAMEL_SJMS.option(),
            ACTIVEMQ.option(),
            streamBundle(
                TinyBundles.bundle()
                    .read(
                        Files.newInputStream(
                            Paths.get("target")
                                .resolve("camel-example-cdi-osgi.jar")))
                    .build()),
            when(false)
                .useOptions(
                    debugConfiguration("5005", true))
        );
    }

    @Test
    public void testRouteStatus() {
        assertThat("Route status is incorrect!",
            context.getRouteStatus("consumer-route"), equalTo(ServiceStatus.Started));
    }

    @Test
    public void testExchangesCompleted() throws Exception {
        ManagedRouteMBean route = context.getManagedRoute(context.getRoute("consumer-route").getId(), ManagedRouteMBean.class);
        assertThat("Number of exchanges completed is incorrect!",
            route.getExchangesCompleted(), equalTo(1L));
    }

    @Test
    public void testExecuteCommands() throws Exception {
        Session session = sessionFactory.create(System.in, System.out, System.err);
        session.execute("camel:context-list");
        session.execute("camel:route-list");
        session.execute("camel:route-info consumer-route");
    }
}
