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
package org.apache.camel.component.whatsapp.integration;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.whatsapp.WhatsAppTestSupport;
import org.apache.camel.component.whatsapp.model.Address;
import org.apache.camel.component.whatsapp.model.ContactMessage;
import org.apache.camel.component.whatsapp.model.ContactMessageRequest;
import org.apache.camel.component.whatsapp.model.Email;
import org.apache.camel.component.whatsapp.model.InteractiveMessageRequest;
import org.apache.camel.component.whatsapp.model.LocationMessage;
import org.apache.camel.component.whatsapp.model.LocationMessageRequest;
import org.apache.camel.component.whatsapp.model.MediaMessage;
import org.apache.camel.component.whatsapp.model.MediaMessageRequest;
import org.apache.camel.component.whatsapp.model.MessageResponse;
import org.apache.camel.component.whatsapp.model.Name;
import org.apache.camel.component.whatsapp.model.Org;
import org.apache.camel.component.whatsapp.model.Phone;
import org.apache.camel.component.whatsapp.model.TemplateMessageRequest;
import org.apache.camel.component.whatsapp.model.TextMessage;
import org.apache.camel.component.whatsapp.model.TextMessageRequest;
import org.apache.camel.component.whatsapp.model.UploadMedia;
import org.apache.camel.component.whatsapp.model.UploadMediaRequest;
import org.apache.camel.component.whatsapp.model.Url;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "WHATSAPP_AUTHORIZATION_TOKEN", matches = ".*",
                                      disabledReason = "WhatsApp Authorization Token not provided"),
        @EnabledIfEnvironmentVariable(named = "WHATSAPP_PHONE_NUMBER_ID", matches = ".*",
                                      disabledReason = "WhatsApp phone number ID not provided"),
        @EnabledIfEnvironmentVariable(named = "WHATSAPP_RECIPIENT_PHONE_NUMBER", matches = ".*",
                                      disabledReason = "WhatsApp recipient phone number not provided") })
public class WhatsAppServiceIT extends WhatsAppTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testTextMessage() {
        TextMessageRequest request = new TextMessageRequest();
        request.setTo(recipientPhoneNumber);
        request.setText(new TextMessage());
        request.getText().setBody("This is an auto-generated message from Camel \uD83D\uDC2B \uD83D\uDC2A");

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testTextMessageWithUrl() {
        TextMessageRequest request = new TextMessageRequest();
        request.setTo(recipientPhoneNumber);
        request.setText(new TextMessage());
        request.getText().setBody("This is an auto-generated message from Camel with URL https://camel.apache.org/");
        request.getText().setPreviewUrl(true);

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testLocationMessage() {
        LocationMessageRequest request = new LocationMessageRequest();
        request.setTo(recipientPhoneNumber);
        request.setLocation(new LocationMessage());
        request.getLocation().setLatitude("41.902782");
        request.getLocation().setLongitude("12.496366");
        request.getLocation().setAddress("Rome");
        request.getLocation().setName("Rome");

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testContactMessage() {
        ContactMessageRequest request = new ContactMessageRequest();
        request.setTo(recipientPhoneNumber);

        ContactMessage contactMessage = getContactMessage();

        request.setContacts(List.of(contactMessage));

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testInteractiveListMessage() throws IOException {
        InteractiveMessageRequest request = MAPPER.readValue(
                WhatsAppServiceIT.class.getResourceAsStream("/interactive-list-message.json"), InteractiveMessageRequest.class);
        request.setTo(recipientPhoneNumber);

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testInteractiveButtonMessage() throws IOException {
        InteractiveMessageRequest request
                = MAPPER.readValue(WhatsAppServiceIT.class.getResourceAsStream("/interactive-button-message.json"),
                        InteractiveMessageRequest.class);
        request.setTo(recipientPhoneNumber);

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    private void assertResponse(MessageResponse response) {
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getMessages()).isNotNull();
        Assertions.assertThat(response.getMessages().get(0).getId()).isNotNull();
    }

    @Test
    public void testMediaStickerMessage() {
        MediaMessage mediaMessage = new MediaMessage();
        // 512x512 sticker only
        mediaMessage.setLink("https://stickercommunity.com/uploads/main/09-05-2021-01-39-24fzl6i-sticker0.webp");

        MediaMessageRequest request = new MediaMessageRequest("sticker", mediaMessage);
        request.setTo(recipientPhoneNumber);

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testMediaUploadImage() throws URISyntaxException {
        UploadMediaRequest uploadMediaRequest = new UploadMediaRequest();
        UploadMedia uploadMedia = new UploadMedia(
                new File(WhatsAppServiceIT.class.getResource("/camel.jpg").toURI()),
                "image/jpeg");
        uploadMediaRequest.setUploadMedia(uploadMedia);

        // Upload media
        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, uploadMediaRequest);

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getId()).isNotNull();

        MediaMessage mediaMessage = new MediaMessage();
        mediaMessage.setId(response.getId());

        MediaMessageRequest request = new MediaMessageRequest("image", mediaMessage);
        request.setTo(recipientPhoneNumber);

        response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testMediaUploadVideoWithInputStream() {
        UploadMediaRequest uploadMediaRequest = new UploadMediaRequest();
        UploadMedia uploadMedia = new UploadMedia(
                "sample.mp4",
                WhatsAppServiceIT.class.getResourceAsStream("/sample.mp4"),
                "video/mp4");
        uploadMediaRequest.setUploadMedia(uploadMedia);

        // Upload media
        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, uploadMediaRequest);

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getId()).isNotNull();

        MediaMessage mediaMessage = new MediaMessage();
        mediaMessage.setId(response.getId());

        MediaMessageRequest request = new MediaMessageRequest("video", mediaMessage);
        request.setTo(recipientPhoneNumber);

        response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testTemplateMessage() throws IOException {
        TemplateMessageRequest request
                = MAPPER.readValue(WhatsAppServiceIT.class.getResourceAsStream("/template-message.json"),
                        TemplateMessageRequest.class);
        request.setTo(recipientPhoneNumber);

        MessageResponse response = (MessageResponse) template.requestBody("whatsapp://" + phoneNumberId, request);

        assertResponse(response);
    }

    @Test
    public void testWrongRecipient() {
        TextMessageRequest request = new TextMessageRequest();
        request.setTo("33333333");
        request.setText(new TextMessage());
        request.getText().setBody("Wrong recipient");

        Assertions.assertThatThrownBy(() -> template.requestBody("whatsapp://" + phoneNumberId, request))
                .getRootCause()
                .hasMessageContaining("Recipient phone number not in allowed list");
    }

    private ContactMessage getContactMessage() {
        ContactMessage contactMessage = new ContactMessage();
        Address address = new Address();
        address.setCity("Rome");
        address.setCountry("Italy");
        address.setCountryCode("ITA");
        address.setState("Italy");
        address.setStreet("Via Rome");
        address.setType("HOME");
        address.setZip("00145");
        contactMessage.setAddresses(List.of(address));
        contactMessage.setName(new Name());
        contactMessage.getName().setFirstName("first");
        contactMessage.getName().setFormattedName("formatted");
        contactMessage.getName().setLastName("last");
        contactMessage.getName().setMiddleName("middle");
        contactMessage.getName().setPrefix("prefix");
        contactMessage.getName().setSuffix("suffix");
        contactMessage.setBirthday(LocalDate.now());
        Email email = new Email();
        email.setEmail("test@camel.com");
        email.setType("WORK");
        contactMessage.setEmails(List.of(email));
        contactMessage.setOrg(new Org());
        contactMessage.getOrg().setCompany("company");
        contactMessage.getOrg().setDepartment("department");
        contactMessage.getOrg().setTitle("title");
        Phone phone = new Phone();
        phone.setPhone("333111333");
        phone.setType("WORK");
        contactMessage.setPhones(List.of(phone));
        Url url = new Url();
        url.setUrl("https://camel.apache.org/");
        url.setType("WORK");
        contactMessage.setUrls(List.of(url));

        return contactMessage;
    }
}
