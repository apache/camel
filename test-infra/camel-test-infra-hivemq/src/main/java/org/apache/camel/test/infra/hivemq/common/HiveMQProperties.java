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
package org.apache.camel.test.infra.hivemq.common;

public final class HiveMQProperties {
    public static final String HIVEMQ_SERVICE_MQTT_HOST = "hivemq.service.mqtt.host";
    public static final String HIVEMQ_SERVICE_MQTT_PORT = "hivemq.service.mqtt.port";
    public static final String HIVEMQ_SERVICE_MQTT_HOST_ADDRESS = "hivemq.service.mqtt.hostaddress";
    public static final String HIVEMQ_SERVICE_USER_NAME = "hivemq.service.user.name";
    public static final String HIVEMQ_SERVICE_USER_PASSWORD = "hivemq.service.user.password";

    public static final String HIVEMQ_CONTAINER = "hivemq.container";
    public static final String HIVEMQ_RESOURCE_PATH = "hivemq.resource.path";
    public static final String HIVEMQ_SPARKPLUG_CONTAINER = "hivemq.sparkplug.container";
    public static final String HIVEMQ_SPARKPLUG_INSTANCE_SELECTOR = "hivemq-sparkplug";

    public static final String HIVEMQ_TEST_SERVICE_NAME = "hivemq";
    public static final String HIVEMQ_PROPERTY_NAME_FORMAT = "%s.instance.type";
    public static final String HIVEMQ_INSTANCE_TYPE = String.format(HIVEMQ_PROPERTY_NAME_FORMAT, HIVEMQ_TEST_SERVICE_NAME);

    private HiveMQProperties() {
    }
}
