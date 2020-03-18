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
package org.apache.camel.component.soroushbot.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.sse.SseEventSink;

import org.apache.camel.component.soroushbot.IOUtils;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.models.response.SoroushResponse;
import org.apache.camel.component.soroushbot.models.response.UploadFileResponse;
import org.apache.logging.log4j.LogManager;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.sse.OutboundEvent;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class SoroushBotWS {
    static Random random = new Random(System.currentTimeMillis());
    static Map<String, Integer> tokenCount = new ConcurrentHashMap<>();
    static List<SoroushMessage> receivedMessages = new ArrayList<>();
    static Map<String, String> fileIdToContent = new ConcurrentHashMap<>();
    List<String> userIds = new ArrayList<>();
    String botId = "botId";

    public SoroushBotWS() {
        //users of system
        for (int i = 0; i < 4; i++) {
            userIds.add("u" + i);
        }
    }

    public static List<SoroushMessage> getReceivedMessages() {
        return receivedMessages;
    }

    public static Map<String, String> getFileIdToContent() {
        return fileIdToContent;
    }

    public static void clear() {
        receivedMessages.clear();
        fileIdToContent.clear();
        tokenCount.clear();
    }

    @GET
    @Path("{token}/getMessage")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getMessage(@PathParam("token") String token, @Context SseEventSink sink) {
        int messageCount = getNumberOfMessage(token);
        int delay = getMessageDelay(token);
        LogManager.getLogger().info("new connection for getting " + messageCount + " message");
        final boolean withFile = token.toLowerCase().contains("file");
//        final EventOutput eventOutput = new EventOutput();
        new Thread(() -> {
            try {
                for (int i = 0; i < messageCount; i++) {
                    final OutboundEvent.Builder eventBuilder
                            = new OutboundEvent.Builder();
                    eventBuilder.id(UUID.randomUUID().toString());
                    eventBuilder.data(SoroushMessage.class, getSoroushMessage(i, withFile));
                    eventBuilder.mediaType(MediaType.APPLICATION_JSON_TYPE);
                    final OutboundEvent event = eventBuilder.build();
                    if (!sink.isClosed()) {
                        sink.send(event);
                    }
//                    eventOutput.write(event);
                    Thread.sleep(delay);
                }
                if (token.toLowerCase().contains("close")) {
                    sink.close();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }).start();
    }

    private int getMessageDelay(String token) {
        Scanner s = new Scanner(token);
        while (s.hasNext()) {
            if ("delay".equalsIgnoreCase(s.next())) {
                if (s.hasNextInt()) {
                    return s.nextInt();
                }
            }
        }
        return 10;
    }

    @POST
    @Path("{token}/sendMessage")
    public Response sendMessage(SoroushMessage soroushMessage, @PathParam("token") String token) {
        String tokenLower = token.toLowerCase();
        Scanner s = new Scanner(tokenLower);
        if (s.next().equals("retry")) {
            int retryCount = s.nextInt();
            Integer currentCount = tokenCount.getOrDefault(tokenLower, 0);
            if (currentCount < retryCount) {
                tokenCount.put(tokenLower, currentCount + 1);
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
            }

        }
        receivedMessages.add(soroushMessage);
        return Response.ok(new SoroushResponse(200, "OK")).build();
    }

    @POST
    @Path("{token}/uploadFile")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@PathParam("token") String token,
                               @FormDataParam("file") InputStream fileInputStream,
                               @FormDataParam("file") FormDataContentDisposition fileMetaData) throws IOException {
        String key = Integer.toString(random.nextInt());
        fileIdToContent.put(key, new String(IOUtils.readFully(fileInputStream, -1, false)));
        return Response.ok(new UploadFileResponse(200, "OK", key)).build();
    }

    @GET
    @Path("{token}/downloadFile/{key}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFile(@PathParam("token") String token, @PathParam("key") String key) {
        String content = fileIdToContent.get(key);
        if (content == null) {
            return Response.status(404).build();
        }
        StreamingOutput fileStream = new StreamingOutput() {
            @Override
            public void write(java.io.OutputStream output) throws IOException, WebApplicationException {
                try {
                    output.write(content.getBytes());
                    output.flush();
                    output.close();
                } catch (Exception e) {
                    throw new WebApplicationException("File Not Found !!");
                }
            }
        };
        return Response
                .ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename = file")
                .build();
    }

    private int getNumberOfMessage(String token) {
        token = token.replaceAll("[^0-9]", " ");
        Scanner s = new Scanner(token);
        if (s.hasNextInt()) {
            return s.nextInt();
        }
        return 10;
    }

    private SoroushMessage getSoroushMessage(int i, boolean withFile) {
        SoroushMessage message = new SoroushMessage();
        message.setFrom(userIds.get(i % 4));
        message.setTo(botId);
        message.setBody("message body " + i);
        if (withFile && i % 4 != 0) {
            String key = Integer.toString(random.nextInt());
            fileIdToContent.put(key, Integer.toString(random.nextInt()));
            message.setFileUrl(key);
            if (i % 2 != 0) {
                String thumbnailKey = Integer.toString(random.nextInt());
                fileIdToContent.put(thumbnailKey, Integer.toString(random.nextInt()));
                message.setFileUrl(thumbnailKey);
            }
        }
        return message;
    }
}