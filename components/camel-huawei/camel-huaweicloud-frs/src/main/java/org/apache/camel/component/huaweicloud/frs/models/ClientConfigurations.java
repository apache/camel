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
package org.apache.camel.component.huaweicloud.frs.models;

import org.apache.camel.component.huaweicloud.common.models.InputSourceType;

public class ClientConfigurations {

    private String accessKey;

    private String secretKey;

    private String projectId;

    private String proxyHost;

    private int proxyPort;

    private String proxyUser;

    private String proxyPassword;

    private boolean ignoreSslVerification;

    private String endpoint;

    private String imageBase64;

    private String imageUrl;

    private String imageFilePath;

    private String anotherImageBase64;

    private String anotherImageUrl;

    private String anotherImageFilePath;

    private String videoBase64;

    private String videoUrl;

    private String videoFilePath;

    private String actions;

    private String actionTimes;

    private InputSourceType inputSourceType;

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageFilePath() {
        return imageFilePath;
    }

    public void setImageFilePath(String imageFilePath) {
        this.imageFilePath = imageFilePath;
    }

    public String getAnotherImageBase64() {
        return anotherImageBase64;
    }

    public void setAnotherImageBase64(String anotherImageBase64) {
        this.anotherImageBase64 = anotherImageBase64;
    }

    public String getAnotherImageUrl() {
        return anotherImageUrl;
    }

    public void setAnotherImageUrl(String anotherImageUrl) {
        this.anotherImageUrl = anotherImageUrl;
    }

    public String getAnotherImageFilePath() {
        return anotherImageFilePath;
    }

    public void setAnotherImageFilePath(String anotherImageFilePath) {
        this.anotherImageFilePath = anotherImageFilePath;
    }

    public String getVideoBase64() {
        return videoBase64;
    }

    public void setVideoBase64(String videoBase64) {
        this.videoBase64 = videoBase64;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getVideoFilePath() {
        return videoFilePath;
    }

    public void setVideoFilePath(String videoFilePath) {
        this.videoFilePath = videoFilePath;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public String getActionTimes() {
        return actionTimes;
    }

    public void setActionTimes(String actionTimes) {
        this.actionTimes = actionTimes;
    }

    public InputSourceType getInputSourceType() {
        return inputSourceType;
    }

    public void setInputSourceType(InputSourceType inputSourceType) {
        this.inputSourceType = inputSourceType;
    }

}
