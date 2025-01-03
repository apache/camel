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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import java.util.Map;

public final class KnativeServiceBuilder {
    private Map<String, String> annotations;
    private Boolean auto;
    private String autoscalingMetric;
    private Long autoscalingTarget;
    private KnativeService.Class _class;
    private Boolean enabled;
    private Long maxScale;
    private Long minScale;
    private String rolloutDuration;
    private Long timeoutSeconds;
    private KnativeService.Visibility visibility;

    private KnativeServiceBuilder() {
    }

    public static KnativeServiceBuilder knativeService() {
        return new KnativeServiceBuilder();
    }

    public KnativeServiceBuilder withAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
        return this;
    }

    public KnativeServiceBuilder withAuto(Boolean auto) {
        this.auto = auto;
        return this;
    }

    public KnativeServiceBuilder withAutoscalingMetric(String autoscalingMetric) {
        this.autoscalingMetric = autoscalingMetric;
        return this;
    }

    public KnativeServiceBuilder withAutoscalingTarget(Long autoscalingTarget) {
        this.autoscalingTarget = autoscalingTarget;
        return this;
    }

    public KnativeServiceBuilder with_class(KnativeService.Class _class) {
        this._class = _class;
        return this;
    }

    public KnativeServiceBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public KnativeServiceBuilder withMaxScale(Long maxScale) {
        this.maxScale = maxScale;
        return this;
    }

    public KnativeServiceBuilder withMinScale(Long minScale) {
        this.minScale = minScale;
        return this;
    }

    public KnativeServiceBuilder withRolloutDuration(String rolloutDuration) {
        this.rolloutDuration = rolloutDuration;
        return this;
    }

    public KnativeServiceBuilder withTimeoutSeconds(Long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public KnativeServiceBuilder withVisibility(KnativeService.Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public KnativeService build() {
        KnativeService knativeService = new KnativeService();
        knativeService.setAnnotations(annotations);
        knativeService.setAuto(auto);
        knativeService.setAutoscalingMetric(autoscalingMetric);
        knativeService.setAutoscalingTarget(autoscalingTarget);
        knativeService.set_class(_class);
        knativeService.setEnabled(enabled);
        knativeService.setMaxScale(maxScale);
        knativeService.setMinScale(minScale);
        knativeService.setRolloutDuration(rolloutDuration);
        knativeService.setTimeoutSeconds(timeoutSeconds);
        knativeService.setVisibility(visibility);
        return knativeService;
    }
}
