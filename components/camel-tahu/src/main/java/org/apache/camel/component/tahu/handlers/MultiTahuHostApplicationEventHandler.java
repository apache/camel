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
package org.apache.camel.component.tahu.handlers;

import java.util.function.BiConsumer;

import org.eclipse.tahu.host.api.MultiHostApplicationEventHandler;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Message;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.mqtt.MqttServerName;

public class MultiTahuHostApplicationEventHandler implements MultiHostApplicationEventHandler {

    private final BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer;
    private final BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer;

    MultiTahuHostApplicationEventHandler(BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer,
                                         BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer) {
        this.onMessageConsumer = onMessageConsumer;
        this.onMetricConsumer = onMetricConsumer;
    }

    @Override
    public void onNodeBirthArrived(MqttServerName serverName, EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeBirthComplete(MqttServerName serverName, EdgeNodeDescriptor edgeNodeDescriptor) {
    }

    @Override
    public void onNodeDataArrived(MqttServerName serverName, EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeDataComplete(MqttServerName serverName, EdgeNodeDescriptor edgeNodeDescriptor) {
    }

    @Override
    public void onNodeDeath(MqttServerName serverName, EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeDeathComplete(MqttServerName serverName, EdgeNodeDescriptor edgeNodeDescriptor) {
    }

    @Override
    public void onDeviceBirthArrived(MqttServerName serverName, DeviceDescriptor deviceDescriptor, Message message) {
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceBirthComplete(MqttServerName serverName, DeviceDescriptor deviceDescriptor) {
    }

    @Override
    public void onDeviceDataArrived(MqttServerName serverName, DeviceDescriptor deviceDescriptor, Message message) {
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceDataComplete(MqttServerName serverName, DeviceDescriptor deviceDescriptor) {
    }

    @Override
    public void onDeviceDeath(MqttServerName serverName, DeviceDescriptor deviceDescriptor, Message message) {
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceDeathComplete(MqttServerName serverName, DeviceDescriptor deviceDescriptor) {
    }

    @Override
    public void onBirthMetric(MqttServerName serverName, SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onDataMetric(MqttServerName serverName, SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onStale(MqttServerName serverName, SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onMessage(MqttServerName serverName, SparkplugDescriptor sparkplugDescriptor, Message message) {
        if (sparkplugDescriptor.isDeviceDescriptor()) {
            onMessageConsumer.accept((DeviceDescriptor) sparkplugDescriptor, message);
        } else {
            onMessageConsumer.accept((EdgeNodeDescriptor) sparkplugDescriptor, message);
        }
    }

    private void acceptMetric(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        if (sparkplugDescriptor.isDeviceDescriptor()) {
            onMetricConsumer.accept((DeviceDescriptor) sparkplugDescriptor, metric);
        } else {
            onMetricConsumer.accept((EdgeNodeDescriptor) sparkplugDescriptor, metric);
        }
    }

    @Override
    public void onConnect(MqttServerName serverName) {
    }

    @Override
    public void onDisconnect(MqttServerName serverName) {
    }

}
