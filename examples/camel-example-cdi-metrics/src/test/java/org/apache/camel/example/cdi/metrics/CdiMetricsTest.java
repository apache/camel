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
package org.apache.camel.example.cdi.metrics;

import javax.inject.Inject;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.annotation.Metric;
import io.astefanutti.metrics.cdi.MetricsExtension;
import org.apache.camel.CamelContext;
import org.apache.camel.cdi.CdiCamelExtension;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class CdiMetricsTest {

    @Inject
    private Meter generated;
    @Inject
    private Meter attempt;
    @Inject
    private Meter success;
    @Inject
    private Meter redelivery;
    @Inject
    private Meter error;

    @Inject
    @Metric(name = "success-ratio")
    private Gauge<Double> ratio;

    @Inject
    private CamelContext context;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Metrics CDI
            .addPackage(MetricsExtension.class.getPackage())
            // Test classes
            .addPackage(Application.class.getPackage())
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testContextName() {
        assertThat("Context name is incorrect!", context.getName(), is(equalTo("camel-example-metrics-cdi")));
    }

    @Test
    public void testMetricsValues() throws Exception {
        // Wait a while so that the timer can kick in
        Thread.sleep(5000);

        // And stop the Camel context so that inflight exchanges get completed
        context.stop();

        assertThat("Meter counts are not consistent!",
            attempt.getCount() - redelivery.getCount() - success.getCount() - error.getCount(),
            is(equalTo(0L)));

        assertThat("Success rate gauge value is incorrect!",
            ratio.getValue(),
            is(equalTo(success.getOneMinuteRate() / generated.getOneMinuteRate())));
    }
}
