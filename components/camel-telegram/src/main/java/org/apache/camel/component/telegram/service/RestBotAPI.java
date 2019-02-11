/**
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
package org.apache.camel.component.telegram.service;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.camel.component.telegram.model.EditMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.model.StopMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;

/**
 * Describes the Telegram Bot APIs.
 */
@Path("/")
public interface RestBotAPI {

    String BOT_API_DEFAULT_URL = "https://api.telegram.org";

    @GET
    @Path("/bot{authorizationToken}/getUpdates")
    @Produces(MediaType.APPLICATION_JSON)
    UpdateResult getUpdates(
            @PathParam("authorizationToken") String authorizationToken,
            @QueryParam("offset") Long offset,
            @QueryParam("limit") Integer limit,
            @QueryParam("timeout") Integer timeoutSeconds);

    @POST
    @Path("/bot{authorizationToken}/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendMessage(
            @PathParam("authorizationToken") String authorizationToken,
            OutgoingTextMessage message);

    @POST
    @Path("/bot{authorizationToken}/sendPhoto")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendPhoto(@PathParam("authorizationToken") String authorizationToken, List<Attachment> attachments);

    @POST
    @Path("/bot{authorizationToken}/sendAudio")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendAudio(@PathParam("authorizationToken") String authorizationToken, List<Attachment> attachments);

    @POST
    @Path("/bot{authorizationToken}/sendVideo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendVideo(@PathParam("authorizationToken") String authorizationToken, List<Attachment> attachments);

    @POST
    @Path("/bot{authorizationToken}/sendDocument")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendDocument(@PathParam("authorizationToken") String authorizationToken, List<Attachment> attachments);

    @POST
    @Path("/bot{authorizationToken}/sendLocation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendLocation(@PathParam("authorizationToken") String authorizationToken, SendLocationMessage location);

    @POST
    @Path("/bot{authorizationToken}/editMessageLiveLocation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult editMessageLiveLocation(@PathParam("authorizationToken") String authorizationToken, EditMessageLiveLocationMessage message);

    @POST
    @Path("/bot{authorizationToken}/stopMessageLiveLocation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult stopMessageLiveLocation(@PathParam("authorizationToken") String authorizationToken, StopMessageLiveLocationMessage message);

    @POST
    @Path("/bot{authorizationToken}/sendVenue")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    MessageResult sendVenue(@PathParam("authorizationToken") String authorizationToken, SendVenueMessage location);
}