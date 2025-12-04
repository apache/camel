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

import org.eclipse.tahu.host.api.HostApplicationEventHandler;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Message;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugDescriptor;

public class TahuHostApplicationEventHandler implements HostApplicationEventHandler {

    private final BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer;
    private final BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer;

    TahuHostApplicationEventHandler(
            BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer,
            BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer) {
        this.onMessageConsumer = onMessageConsumer;
        this.onMetricConsumer = onMetricConsumer;
    }

    @Override
    public void onNodeBirthArrived(EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeBirthComplete(EdgeNodeDescriptor edgeNodeDescriptor) {}

    @Override
    public void onNodeDataArrived(EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeDataComplete(EdgeNodeDescriptor edgeNodeDescriptor) {}

    @Override
    public void onNodeDeath(EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeDeathComplete(EdgeNodeDescriptor edgeNodeDescriptor) {}

    @Override
    public void onDeviceBirthArrived(DeviceDescriptor deviceDescriptor, Message message) {
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceBirthComplete(DeviceDescriptor deviceDescriptor) {}

    @Override
    public void onDeviceDataArrived(DeviceDescriptor deviceDescriptor, Message message) {
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceDataComplete(DeviceDescriptor deviceDescriptor) {}

    @Override
    public void onDeviceDeath(DeviceDescriptor deviceDescriptor, Message message) {
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceDeathComplete(DeviceDescriptor deviceDescriptor) {}

    @Override
    public void onBirthMetric(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onDataMetric(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onStale(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onMessage(SparkplugDescriptor sparkplugDescriptor, Message message) {
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
    public void onConnect() {}

    @Override
    public void onDisconnect() {}
}
