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

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.annotation.Metric;

class SuccessRatioGauge {

    @Inject
    private Meter generated;

    @Inject
    private Meter success;

    @Produces
    @Metric(name = "success-ratio")
    private Gauge<Double> successRatio = new RatioGauge() {
        @Override
        protected Ratio getRatio() {
            return Ratio.of(success.getOneMinuteRate(), generated.getOneMinuteRate());
        }
    };
}
