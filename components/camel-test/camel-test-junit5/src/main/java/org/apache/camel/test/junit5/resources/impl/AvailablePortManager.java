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
package org.apache.camel.test.junit5.resources.impl;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.resources.AvailablePort;
import org.apache.camel.test.junit5.resources.BaseResourceManager;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AvailablePortManager extends BaseResourceManager<AvailablePort> {

    public AvailablePortManager() {
        super(AvailablePort.class);
    }

    @Override
    protected Holder createHolder(ExtensionContext context, Field field) {
        return new Holder() {
            AvailablePortFinder.Port[] ports;

            @Override
            public synchronized Object get() {
                if (ports == null) {
                    AvailablePort ap = field.getAnnotation(AvailablePort.class);
                    ports = new AvailablePortFinder.Port[ap.size()];
                    for (int i = 0; i < ports.length; i++) {
                        ports[i] = AvailablePortFinder.find();
                    }
                }
                if (field.getType() == int.class) {
                    return ports[0].getPort();
                } else {
                    int[] ints = new int[ports.length];
                    for (int i = 0; i < ports.length; i++) {
                        ints[i] = ports[i].getPort();
                    }
                    return ints;
                }
            }

            @Override
            public void close() {
                if (ports != null) {
                    Arrays.stream(ports).forEach(AvailablePortFinder.Port::release);
                }
            }
        };
    }

    @Override
    protected void verifyType(Field field, AvailablePort ap) {
        Class<?> type = field.getType();
        if (type == int.class) {
            if (ap.size() != 1) {
                throw new ExtensionConfigurationException(
                        "The size on @AvailablePort field [" + field + "] must be 1 but was: " + ap.size());
            }
        } else if (type == int[].class) {
            if (ap.size() < 1) {
                throw new ExtensionConfigurationException(
                        "The size on @AvailablePort field [" + field + "] must be greater or equals to 1 but was: "
                                                          + ap.size());
            }
        } else {
            throw new ExtensionConfigurationException(
                    "Can only resolve @AvailablePort field [" + field + "] of type int or int[] but was: " + type.getName());
        }
        super.verifyType(field, ap);
    }

}
