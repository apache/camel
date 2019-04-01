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

import org.apache.camel.component.soroushbot.models.ConnectionType;
import org.apache.camel.component.soroushbot.models.response.SoroushResponse;
import org.apache.camel.component.soroushbot.utils.SoroushException;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import javax.validation.constraints.NotNull;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * singleton class that allows interacting with the Soroush server to exchange messages.
 */
public class SoroushService {
    private static final String url = "https://bot.sapp.ir";
    private static SoroushService soroushService;
    /**
     * allow Soroush server to be mocked for testing,
     * during testing soroush service will be connected to the alternativeUrl if provided
     */
    private String alternativeUrl = null;

    private SoroushService() {
    }

    /**
     * @return soroush server instance.
     */
    public static SoroushService get() {
        if (soroushService != null) return soroushService;
        synchronized (SoroushService.class) {
            if (soroushService != null) return soroushService;
            soroushService = new SoroushService();
            return soroushService;
        }
    }

    /**
     * create fully qualified URL, given the token connection type and fileId if needed.
     *
     * @param token
     * @param type
     * @param fileId
     * @return
     */
    public final String generateUrl(@NotNull String token, @NotNull ConnectionType type, String fileId) {
        return getCurrentUrl() + "/" + token + "/" + type.value() + (fileId != null ? "/" + fileId : "");
    }

    private String getCurrentUrl() {
        if (alternativeUrl != null) return alternativeUrl;
        return url;
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
                .target(generateUrl(token, ConnectionType.uploadFile, null));
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
                .target(generateUrl(token, ConnectionType.sendMessage, null));
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
                .target(generateUrl(token, ConnectionType.downloadFile, fileId));
    }

    /**
     * check if the response is successfully sent to soroush, by default it assumes that the response type is SoroushResponse
     *
     * @param response the response
     * @return SoroushResponse
     * @throws IOException      if can not connect to soroush server
     * @throws SoroushException if soroush reject the response
     */
    public SoroushResponse assertSuccessful(Response response) throws IOException, SoroushException {
        return assertSuccessful(response, SoroushResponse.class);
    }

    /**
     * throw IOException
     * if the exception is instance of SoroushException it indicates that the soroush does not accept the message
     * and therefore resending the request will never be succeed
     *
     * @param response
     * @param responseType expecting response type from soroush
     * @param <T>          the class that we expect the response should be of this type
     * @throws IOException      if sending message to soroush server is not successful
     * @throws SoroushException if soroush reject the response with an error code
     */
    public <T> T assertSuccessful(Response response, Class<T> responseType) throws IOException, SoroushException {
        int status = response.getStatus();
        if (status == 503 || status == 429 || status == 301) {
            String message = response.readEntity(String.class);
            throw new IOException("code: " + status + " message:" + message);
        }
        if (status >= 300) {
            throw new SoroushException(status, response.readEntity(String.class));
        }
        if (SoroushResponse.class.isAssignableFrom(responseType)) {
            Class<? extends SoroushResponse> SoroushResponseType = responseType.asSubclass(SoroushResponse.class);
            SoroushResponse soroushResponse = response.readEntity(SoroushResponseType);
            if (soroushResponse.getResultCode() != 200) {
                throw new SoroushException(soroushResponse, status, soroushResponse.toString());
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
