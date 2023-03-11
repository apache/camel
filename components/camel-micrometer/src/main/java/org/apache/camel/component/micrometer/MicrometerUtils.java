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
package org.apache.camel.component.micrometer;

import java.util.Set;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MicrometerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerUtils.class);

    private MicrometerUtils() {

    }

    public static Meter.Type getByName(String meterName) {
        switch (meterName) {
            case "summary":
                return Meter.Type.DISTRIBUTION_SUMMARY;
            case "counter":
                return Meter.Type.COUNTER;
            case "timer":
                return Meter.Type.TIMER;
            default:
                throw new RuntimeCamelException("Unsupported meter type " + meterName);
        }
    }

    public static String getName(Meter.Type type) {
        switch (type) {
            case DISTRIBUTION_SUMMARY:
                return "summary";
            case COUNTER:
                return "counter";
            case TIMER:
                return "timer";
            default:
                throw new RuntimeCamelException("Unsupported meter type " + type);
        }
    }

    public static MeterRegistry getOrCreateMeterRegistry(Registry camelRegistry, String name) {
        MeterRegistry result = getMeterRegistryFromCamelRegistry(camelRegistry, name);
        if (result == null) {
            LOG.debug("Creating new MeterRegistry: {}", name);
            result = createMeterRegistry();
            // enlist in registry so it can be reused
            camelRegistry.bind(name, result);
        } else {
            LOG.debug("Using existing MeterRegistry: {}", name);
        }
        return result;
    }

    public static MeterRegistry getMeterRegistryFromCamelRegistry(Registry camelRegistry, String registryName) {
        MeterRegistry registry = getMeterRegistryFromCamelRegistry(camelRegistry, registryName,
                CompositeMeterRegistry.class);
        if (registry != null) {
            return registry;
        }

        return getMeterRegistryFromCamelRegistry(camelRegistry, registryName, MeterRegistry.class);
    }

    public static MeterRegistry createMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    private static MeterRegistry getMeterRegistryFromCamelRegistry(
            Registry camelRegistry, String registryName,
            Class<? extends MeterRegistry> registryType) {
        MeterRegistry registry = camelRegistry.lookupByNameAndType(registryName, registryType);
        if (registry != null) {
            return registry;
        } else {
            Set<? extends MeterRegistry> registries = camelRegistry.findByType(registryType);
            if (registries != null && registries.size() == 1) {
                return registryType.cast(registries.iterator().next());
            }
        }
        return null;
    }
}
