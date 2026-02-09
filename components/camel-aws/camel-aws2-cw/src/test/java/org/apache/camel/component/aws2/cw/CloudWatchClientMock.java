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
package org.apache.camel.component.aws2.cw;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchServiceClientConfiguration;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsForMetricRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsForMetricResponse;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;

public class CloudWatchClientMock implements CloudWatchClient {

    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public PutMetricDataResponse putMetricData(PutMetricDataRequest request) {
        PutMetricDataResponse.Builder builder = PutMetricDataResponse.builder();
        return builder.build();
    }

    @Override
    public ListMetricsResponse listMetrics(ListMetricsRequest request) {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(Metric.builder()
                .namespace("TestNamespace")
                .metricName("TestMetric")
                .build());
        metrics.add(Metric.builder()
                .namespace("TestNamespace")
                .metricName("TestMetric2")
                .build());

        return ListMetricsResponse.builder()
                .metrics(metrics)
                .nextToken(null) // No more results
                .build();
    }

    @Override
    public DescribeAlarmsResponse describeAlarms(DescribeAlarmsRequest request) {
        List<MetricAlarm> alarms = new ArrayList<>();
        alarms.add(MetricAlarm.builder()
                .alarmName("TestAlarm")
                .metricName("TestMetric")
                .namespace("TestNamespace")
                .build());

        return DescribeAlarmsResponse.builder()
                .metricAlarms(alarms)
                .nextToken(null) // No more results
                .build();
    }

    @Override
    public DescribeAlarmsForMetricResponse describeAlarmsForMetric(DescribeAlarmsForMetricRequest request) {
        List<MetricAlarm> alarms = new ArrayList<>();
        alarms.add(MetricAlarm.builder()
                .alarmName("TestAlarmForMetric")
                .metricName(request.metricName())
                .namespace(request.namespace())
                .build());

        return DescribeAlarmsForMetricResponse.builder()
                .metricAlarms(alarms)
                .build();
    }

    @Override
    public CloudWatchServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

}
