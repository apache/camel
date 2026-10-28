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
package org.apache.camel.semantic;

import java.util.List;

/**
 * Optional expert/application-owned metrics. Return locally cached snapshots without inference, remote I/O or history
 * scans. Return the complete current set of measurements (at most 64); omit retired series. The producer owns bounded
 * dimensions, units and series identity. Camel replaces this source's latest values in the bounded read-only cache used
 * by management and presentation surfaces. Historical storage and analysis belong to external monitoring systems.
 */
public interface SemanticMetricSource {
    List<SemanticMonitoringSnapshot> snapshots();
}
