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
package org.apache.camel.example.fhir.osgi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.core.TinyBundles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FhirOsgiIT {

    @Inject
    private CamelContext context;

    @Configuration
    public Option[] config() throws IOException {
        return options(
            PaxExamOptions.KARAF.option(),
            PaxExamOptions.CAMEL_FHIR.option(),
            streamBundle(
                TinyBundles.bundle()
                    .read(
                        Files.newInputStream(
                            Paths.get("target")
                                .resolve("camel-example-fhir-osgi.jar")))
                    .build()),
            when(false)
                .useOptions(
                    debugConfiguration("5005", true)),
            CoreOptions.composite(editConfigurationFilePut(
                "etc/org.apache.camel.example.fhir.osgi.configuration.cfg",
                new File("org.apache.camel.example.fhir.osgi.configuration.cfg")))
        );
    }

    @Test
    public void testRouteStatus() {
        assertNotNull(context);
        assertEquals("Route status is incorrect!", ServiceStatus.Started, context.getRouteController().getRouteStatus("fhir-example-osgi"));
    }
}
