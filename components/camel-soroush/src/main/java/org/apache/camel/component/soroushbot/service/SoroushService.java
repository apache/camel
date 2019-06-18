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
package org.apache.camel.component.soroushbot.service;

import java.io.IOException;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.models.response.SoroushResponse;
import org.apache.camel.component.soroushbot.utils.SoroushException;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

/**
 * singleton class that allows interacting with the Soroush server to exchange messages.
 */
public final class SoroushService {
    private static final String URL = "https://bot.sapp.ir";
    private static SoroushService soroushService;
    /**
     * allow Soroush server to be mocked for testing,
     * during testing soroush service will be connected to the alternativeUrl if provided
     */
    private String alternativeUrl;

    private SoroushService() {
    }

    /**
     * @return soroush server instance.
     */
    public static SoroushService get() {
        if (soroushService != null) {
            return soroushService;
        }
        synchronized (SoroushService.class) {
            if (soroushService != null) {
                return soroushService;
            }
            soroushService = new SoroushService();
            return soroushService;
        }
    }

    /**
     * create fully qualified URL, given the token, endpoint and fileId if needed.
     *
     * @param token
     * @param type
     * @param fileId
     * @return
     */
    public String generateUrl(String token, SoroushAction type, String fileId) {
        return getCurrentUrl() + "/" + token + "/" + type.value() + (fileId != null ? "/" + fileId : "");
    }

    private String getCurrentUrl() {
        if (alternativeUrl != null) {
            return alternativeUrl;
        }
        return URL;
    }

    /**
     * create {@link WebTarget } for uploading file to server
     *
     * @param token
     * @param timeOut
     * @return
     */
    public WebTarget createUploadFileTarget(String token, Integer timeOut) {
        return ClientBuilder.newBuilder()
                .property(ClientProperties.CONNECT_TIMEOUT, timeOut)
                .register(MultiPartFeature.class).build()
                .target(generateUrl(token, SoroushAction.uploadFile, null));
    }

    /**
     * create {@link WebTarget } for sending message to server
     *
     * @param token
     * @param timeOut
     * @return
     */

    public WebTarget createSendMessageTarget(String token, Integer timeOut) {
        return ClientBuilder.newBuilder()
                .property(ClientProperties.CONNECT_TIMEOUT, timeOut).build()
                .target(generateUrl(token, SoroushAction.sendMessage, null));
    }

    /**
     * create {@link WebTarget } for downloading file from server
     *
     * @param token
     * @param fileId
     * @param timeOut
     * @return
     */

    public WebTarget createDownloadFileTarget(String token, String fileId, Integer timeOut) {
        return ClientBuilder.newBuilder()
                .property(ClientProperties.CONNECT_TIMEOUT, timeOut).build()
                .target(generateUrl(token, SoroushAction.downloadFile, fileId));
    }

    /**
     * check if the response is successfully sent to soroush, by default it assumes that the response type is SoroushResponse
     *
     * @param response the response
     * @param soroushMessage the message that we are checking its success, only for logging purpose
     * @return SoroushResponse
     * @throws IOException      if can not connect to soroush server
     * @throws SoroushException if soroush reject the response
     */
    public SoroushResponse assertSuccessful(Response response, SoroushMessage soroushMessage) throws IOException, SoroushException {
        return assertSuccessful(response, SoroushResponse.class, soroushMessage);
    }

    /**
     * throw IOException
     * if the exception is instance of SoroushException it indicates that the soroush does not accept the message
     * and therefore resending the request will never be succeed
     *
     * @param <T>          the class that we expect the response should be of this type
     * @param response the response
     * @param responseType expecting response type from soroush
     * @param soroushMessage the message that we are checking its success, only for logging purpose
     * @throws IOException      if sending message to soroush server is not successful
     * @throws SoroushException if soroush reject the response with an error code
     */
    public <T> T assertSuccessful(Response response, Class<T> responseType, SoroushMessage soroushMessage) throws IOException, SoroushException {
        int status = response.getStatus();
        if (status == 503 || status == 429 || status == 301) {
            String message = response.readEntity(String.class);
            throw new IOException("code: " + status + " message:" + message);
        }
        if (status >= 300) {
            throw new SoroushException(soroushMessage, null, status, response.readEntity(String.class));
        }
        if (SoroushResponse.class.isAssignableFrom(responseType)) {
            Class<? extends SoroushResponse> soroushResponseType = responseType.asSubclass(SoroushResponse.class);
            SoroushResponse soroushResponse = response.readEntity(soroushResponseType);
            if (soroushResponse.getResultCode() != 200) {
                String body = soroushResponse.toString();
                throw new SoroushException(soroushMessage, soroushResponse, status, body);
            }
            return (T) soroushResponse;
        } else {
            return response.readEntity(responseType);
        }
    }

    /**
     * set {@code alternativeUrl} that should be used for testing
     *
     * @param alternativeUrl
     */
    public void setAlternativeUrl(String alternativeUrl) {
        this.alternativeUrl = alternativeUrl;
    }
}
