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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "enabled", "schedule", "timezone", "startingDeadlineSeconds", "activeDeadlineSeconds", "backoffLimit",
        "durationMaxIdleSeconds" })
public class CronJob {

    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled = Boolean.FALSE;

    @JsonProperty("schedule")
    @JsonPropertyDescription("The schedule in Cron format.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String schedule;

    @JsonProperty("timezone")
    @JsonPropertyDescription("The time zone name for the given schedule.")
    @JsonSetter(nulls = Nulls.SKIP)
    private String timezone;

    @JsonProperty("startingDeadlineSeconds")
    @JsonPropertyDescription("Optional deadline in seconds for starting the job if it misses scheduled time for any reason.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long startingDeadlineSeconds;

    @JsonProperty("activeDeadlineSeconds")
    @JsonPropertyDescription("Specifies the duration in seconds relative to the startTime that the job may be continuously active before the system tries to terminate it.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long activeDeadlineSeconds;

    @JsonProperty("backoffLimit")
    @JsonPropertyDescription("Specifies the number of retries before marking this job failed.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer backoffLimit;

    @JsonProperty("durationMaxIdleSeconds")
    @JsonPropertyDescription("How long time in seconds Camel can be idle before automatic terminating the JVM, it sets the camel.main.duration-max-idle-seconds property, default to 1.")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer durationMaxIdleSeconds = 1;

    public CronJob() {
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Long getStartingDeadlineSeconds() {
        return startingDeadlineSeconds;
    }

    public void setStartingDeadlineSeconds(Long startingDeadlineSeconds) {
        this.startingDeadlineSeconds = startingDeadlineSeconds;
    }

    public Long getActiveDeadlineSeconds() {
        return activeDeadlineSeconds;
    }

    public void setActiveDeadlineSeconds(Long activeDeadlineSeconds) {
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    public Integer getBackoffLimit() {
        return backoffLimit;
    }

    public void setBackoffLimit(Integer backoffLimit) {
        this.backoffLimit = backoffLimit;
    }

    public Integer getDurationMaxIdleSeconds() {
        return durationMaxIdleSeconds;
    }

    public void setDurationMaxIdleSeconds(Integer durationMaxIdleSeconds) {
        this.durationMaxIdleSeconds = durationMaxIdleSeconds;
    }

}
