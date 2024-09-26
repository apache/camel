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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "annotations", "auto", "autoscalingMetric", "autoscalingTarget", "class", "enabled", "maxScale",
        "minScale", "rolloutDuration", "timeoutSeconds", "visibility" })
public class KnativeService {
    @JsonProperty("annotations")
    @JsonPropertyDescription("The annotations added to route. This can be used to set knative service specific annotations CLI usage example: -t \"knative-service.annotations.'haproxy.router.openshift.io/balance'=true\"")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Map<String, String> annotations;
    @JsonProperty("auto")
    @JsonPropertyDescription("Automatically deploy the camel rouyte as Knative service when all conditions hold: \n * Camel route is using the Knative profile * All routes are either starting from an HTTP based consumer or a passive consumer (e.g. `direct` is a passive consumer)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean auto;
    @JsonProperty("autoscalingMetric")
    @JsonPropertyDescription("Configures the Knative autoscaling metric property (e.g. to set `concurrency` based or `cpu` based autoscaling). \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String autoscalingMetric;
    @JsonProperty("autoscalingTarget")
    @JsonPropertyDescription("Sets the allowed concurrency level or CPU percentage (depending on the autoscaling metric) for each Pod. \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long autoscalingTarget;
    @JsonProperty("class")
    @JsonPropertyDescription("Configures the Knative autoscaling class property (e.g. to set `hpa.autoscaling.knative.dev` or `kpa.autoscaling.knative.dev` autoscaling). \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Class _class;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("maxScale")
    @JsonPropertyDescription("An upper bound for the number of Pods that can be running in parallel for the camel route. Knative has its own cap value that depends on the installation. \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long maxScale;
    @JsonProperty("minScale")
    @JsonPropertyDescription("The minimum number of Pods that should be running at any time for the camel route. It's **zero** by default, meaning that the camel route is scaled down to zero when not used for a configured amount of time. \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long minScale;
    @JsonProperty("rolloutDuration")
    @JsonPropertyDescription("Enables to gradually shift traffic to the latest Revision and sets the rollout duration. It's disabled by default and must be expressed as a Golang `time.Duration` string representation, rounded to a second precision.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String rolloutDuration;
    @JsonProperty("timeoutSeconds")
    @JsonPropertyDescription("The maximum duration in seconds that the request instance is allowed to respond to a request. This field propagates to the camel route pod's terminationGracePeriodSeconds \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long timeoutSeconds;
    @JsonProperty("visibility")
    @JsonPropertyDescription("Setting `cluster-local`, Knative service becomes a private service. Specifically, this option applies the `networking.knative.dev/visibility` label to Knative service. \n Refer to the Knative documentation for more information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Visibility visibility;

    public KnativeService() {
    }

    public Map<String, String> getAnnotations() {
        return this.annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public Boolean getAuto() {
        return this.auto;
    }

    public void setAuto(Boolean auto) {
        this.auto = auto;
    }

    public String getAutoscalingMetric() {
        return this.autoscalingMetric;
    }

    public void setAutoscalingMetric(String autoscalingMetric) {
        this.autoscalingMetric = autoscalingMetric;
    }

    public Long getAutoscalingTarget() {
        return this.autoscalingTarget;
    }

    public void setAutoscalingTarget(Long autoscalingTarget) {
        this.autoscalingTarget = autoscalingTarget;
    }

    public Class get_class() {
        return this._class;
    }

    public void set_class(Class _class) {
        this._class = _class;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getMaxScale() {
        return this.maxScale;
    }

    public void setMaxScale(Long maxScale) {
        this.maxScale = maxScale;
    }

    public Long getMinScale() {
        return this.minScale;
    }

    public void setMinScale(Long minScale) {
        this.minScale = minScale;
    }

    public String getRolloutDuration() {
        return this.rolloutDuration;
    }

    public void setRolloutDuration(String rolloutDuration) {
        this.rolloutDuration = rolloutDuration;
    }

    public Long getTimeoutSeconds() {
        return this.timeoutSeconds;
    }

    public void setTimeoutSeconds(Long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Visibility getVisibility() {
        return this.visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public enum Class {
        @JsonProperty("kpa.autoscaling.knative.dev")
        KPA_AUTOSCALING_KNATIVE_DEV("kpa.autoscaling.knative.dev"),
        @JsonProperty("hpa.autoscaling.knative.dev")
        HPA_AUTOSCALING_KNATIVE_DEV("hpa.autoscaling.knative.dev");

        private final String value;

        Class(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }

    public enum Visibility {
        @JsonProperty("cluster-local")
        CLUSTERLOCAL("cluster-local");

        private final String value;

        Visibility(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
