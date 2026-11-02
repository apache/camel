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
package org.apache.camel.component.huaweicloud.dms;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.dms.constants.DMSProperties;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequestBody;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequestBody;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceResponse;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request of createInstance and updateInstance as JSON text, bytes, a stream or a file. The createInstance endpoint
 * has all the options of a Kafka instance too, so a body that is ignored creates the instance "options-instance".
 */
class InstanceRequestBodyTest extends CamelTestSupport {

    private static final String CREATE_JSON
            = "{\"name\":\"json-instance\",\"engine\":\"kafka\",\"engine_version\":\"2.7\",\"description\":\"Zürich ✓\"}";
    private static final String UPDATE_JSON = "{\"name\":\"new-name\",\"description\":\"Zürich ✓\"}";

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("dmsClient")
    DmsClient mockClient = Mockito.mock(DmsClient.class);

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(
            testConfiguration.getProperty("accessKey"),
            testConfiguration.getProperty("secretKey"));

    @BindToRegistry("availableZones")
    String availableZones = testConfiguration.getProperty("availableZone");

    @TempDir
    Path tempDir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:create")
                        .to("hwcloud-dms:createInstance?" +
                            "serviceKeys=#serviceKeys" +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&dmsClient=#dmsClient" +
                            "&name=options-instance" +
                            "&engine=kafka" +
                            "&engineVersion=" + testConfiguration.getProperty("engineVersion") +
                            "&specification=" + testConfiguration.getProperty("specification") +
                            "&storageSpace=1000" +
                            "&partitionNum=500" +
                            "&vpcId=" + testConfiguration.getProperty("vpcId") +
                            "&securityGroupId=" + testConfiguration.getProperty("securityGroupId") +
                            "&subnetId=" + testConfiguration.getProperty("subnetId") +
                            "&availableZones=#availableZones" +
                            "&productId=" + testConfiguration.getProperty("productId") +
                            "&kafkaManagerUser=" + testConfiguration.getProperty("kafkaManagerUser") +
                            "&kafkaManagerPassword=" + testConfiguration.getProperty("kafkaManagerPassword") +
                            "&storageSpecCode=" + testConfiguration.getProperty("storageSpecCode"));

                from("direct:update")
                        .to("hwcloud-dms:updateInstance?" +
                            "serviceKeys=#serviceKeys" +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&instanceId=" + testConfiguration.getProperty("instanceId") +
                            "&dmsClient=#dmsClient");
            }
        };
    }

    @Test
    void createInstanceReadsJsonText() {
        assertJsonRequest(createInstance(CREATE_JSON));
    }

    @Test
    void createInstanceReadsJsonBytes() {
        assertJsonRequest(createInstance(CREATE_JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void createInstanceReadsJsonStream() {
        assertJsonRequest(createInstance(new ByteArrayInputStream(CREATE_JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void createInstanceReadsJsonFile() throws Exception {
        File file = Files.writeString(tempDir.resolve("request.json"), CREATE_JSON, StandardCharsets.UTF_8).toFile();

        assertJsonRequest(createInstance(file));
    }

    @Test
    void createInstanceReadsJsonOfFileConsumer() throws Exception {
        File file = Files.writeString(tempDir.resolve("request.json"), CREATE_JSON, StandardCharsets.UTF_8).toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());

        assertJsonRequest(createInstance(genericFile));
    }

    @Test
    void createInstanceTakesRequestBody() {
        CreateInstanceRequestBody body = new CreateInstanceRequestBody().withName("pojo-instance").withEngine("rabbitmq");

        assertEquals("pojo-instance", createInstance(body).getName());
    }

    @Test
    void createInstanceWithoutBodyUsesOptions() {
        CreateInstanceRequestBody request = createInstance(null);

        assertEquals("options-instance", request.getName());
        assertEquals(Integer.valueOf(500), request.getPartitionNum());
    }

    @Test
    void createInstanceWithEmptyBodyUsesOptions() {
        assertEquals("options-instance", createInstance(" ").getName());
        Mockito.reset(mockClient);
        assertEquals("options-instance", createInstance(new byte[0]).getName());
    }

    @Test
    void createInstanceFailsOnText() {
        Exception failure = createInstanceFailure("sample_body");

        assertTrue(failure.getMessage().startsWith(
                "Exchange body must be a CreateInstanceRequestBody or a valid JSON representation of it (error at line 1"),
                failure.getMessage());
        assertTrue(failure.getMessage().endsWith("Send no body to create an instance from the endpoint parameters"),
                failure.getMessage());
    }

    @Test
    void createInstanceFailsOnBytesThatAreNotJson() {
        createInstanceFailure(new byte[] { (byte) 0xff, (byte) 0xfe, 'x' });
    }

    @Test
    void createInstanceReadsMap() {
        // such as the JSON unmarshalled by an earlier step
        Map<String, Object> request = Map.of("name", "json-instance", "engine", "kafka", "engine_version", "2.7",
                "description", "Zürich ✓");

        assertJsonRequest(createInstance(request));
    }

    @Test
    void createInstanceFailsOnMapWithUnknownField() {
        Exception failure = createInstanceFailure(Map.of("name", "json-instance", "engineVersion", "2.7"));

        assertTrue(failure.getMessage().contains("of it (unknown field engineVersion). Send no body"), failure.getMessage());
    }

    @Test
    void createInstanceFailureNamesTheFieldWithAWrongValue() {
        Exception failure = createInstanceFailure("{\"name\":\"json-instance\",\"storage_space\":\"secret-value\"}");

        assertTrue(failure.getMessage().contains("(wrong value for field storage_space at line 1, column "),
                failure.getMessage());
        assertFalse(failure.getMessage().contains("secret"), failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void createInstanceFailureNamesTheFieldWithAWrongValueInMap() {
        Exception failure = createInstanceFailure(Map.of("name", "json-instance", "storage_space", "secret-value"));

        assertTrue(failure.getMessage().contains("of it (wrong value for field storage_space). Send no body"),
                failure.getMessage());
        assertFalse(failure.getMessage().contains("secret"), failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void createInstanceFailureNamesTheArrayElementWithAWrongValue() {
        Exception failure = createInstanceFailure(
                "{\"name\":\"json-instance\",\"available_zones\":[\"zone-1\",{\"zone\":\"secret-value\"}]}");

        assertTrue(failure.getMessage().contains("(wrong value for field available_zones[1] at line 1, column "),
                failure.getMessage());
        assertFalse(failure.getMessage().contains("secret"), failure.getMessage());
    }

    @Test
    void createInstanceFailsOnUnknownField() {
        Exception failure = createInstanceFailure("{\"name\":\"json-instance\",\"engineVersion\":\"2.7\"}");

        assertTrue(failure.getMessage().contains("(unknown field engineVersion at line 1, column "), failure.getMessage());
    }

    @Test
    void createInstanceFailureDoesNotQuoteTheBody() {
        Exception failure = createInstanceFailure("{\"name\":\"json-instance\",\"password\":secret-value}");

        assertFalse(failure.getMessage().contains("secret"), failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void updateInstanceReadsJsonText() {
        assertJsonUpdate(updateInstance(UPDATE_JSON));
    }

    @Test
    void updateInstanceReadsJsonBytes() {
        assertJsonUpdate(updateInstance(UPDATE_JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void updateInstanceReadsJsonStream() {
        assertJsonUpdate(updateInstance(new ByteArrayInputStream(UPDATE_JSON.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void updateInstanceReadsJsonFile() throws Exception {
        File file = Files.writeString(tempDir.resolve("update.json"), UPDATE_JSON, StandardCharsets.UTF_8).toFile();

        assertJsonUpdate(updateInstance(file));
    }

    @Test
    void updateInstanceReadsMap() {
        assertJsonUpdate(updateInstance(Map.of("name", "new-name", "description", "Zürich ✓")));
    }

    @Test
    void updateInstanceFailureNamesTheFieldWithAWrongValue() {
        Exception failure = updateInstanceFailure("{\"name\":\"new-name\",\"enable_publicip\":\"secret-value\"}");

        assertTrue(failure.getMessage().contains("(wrong value for field enable_publicip at line 1, column "),
                failure.getMessage());
        assertFalse(failure.getMessage().contains("secret"), failure.getMessage());
    }

    @Test
    void updateInstanceFailsOnText() {
        Exception failure = updateInstanceFailure("sample_body");

        assertTrue(failure.getMessage().startsWith(
                "Exchange body must include an UpdateInstanceRequestBody or a valid JSON representation of it (error at line 1"),
                failure.getMessage());
    }

    @Test
    void updateInstanceFailsOnBytesThatAreNotJson() {
        updateInstanceFailure(new byte[] { (byte) 0xff, (byte) 0xfe, 'x' });
    }

    @Test
    void updateInstanceFailsWithoutBody() {
        Exception failure = updateInstanceFailure(null);

        assertTrue(failure.getMessage().startsWith("Exchange body must include an UpdateInstanceRequestBody"),
                failure.getMessage());
    }

    private static void assertJsonRequest(CreateInstanceRequestBody request) {
        assertEquals("json-instance", request.getName());
        assertEquals("2.7", request.getEngineVersion());
        assertEquals("Zürich ✓", request.getDescription());
        // nothing comes from the options
        assertNull(request.getPartitionNum());
    }

    private static void assertJsonUpdate(UpdateInstanceRequestBody request) {
        assertEquals("new-name", request.getName());
        assertEquals("Zürich ✓", request.getDescription());
    }

    private CreateInstanceRequestBody createInstance(Object body) {
        Mockito.when(mockClient.createInstance(Mockito.any(CreateInstanceRequest.class)))
                .thenReturn(new CreateInstanceResponse().withInstanceId("test-instance-id"));

        Exchange exchange = template.send("direct:create", e -> e.getMessage().setBody(body));

        assertNull(exchange.getException(), () -> "createInstance failed: " + exchange.getException());
        assertEquals("{\"instance_id\":\"test-instance-id\"}", exchange.getMessage().getBody(String.class));
        ArgumentCaptor<CreateInstanceRequest> request = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        Mockito.verify(mockClient).createInstance(request.capture());
        return request.getValue().getBody();
    }

    private Exception createInstanceFailure(Object body) {
        Exchange exchange = template.send("direct:create", e -> e.getMessage().setBody(body));

        assertNotNull(exchange.getException(), "createInstance should fail, the body was ignored");
        Mockito.verify(mockClient, Mockito.never()).createInstance(Mockito.any());
        return assertInstanceOf(IllegalArgumentException.class, exchange.getException());
    }

    private UpdateInstanceRequestBody updateInstance(Object body) {
        Mockito.when(mockClient.updateInstance(Mockito.any(UpdateInstanceRequest.class)))
                .thenReturn(new UpdateInstanceResponse());

        Exchange exchange = template.send("direct:update", e -> e.getMessage().setBody(body));

        assertNull(exchange.getException(), () -> "updateInstance failed: " + exchange.getException());
        assertTrue(exchange.getProperty(DMSProperties.INSTANCE_UPDATED, boolean.class));
        ArgumentCaptor<UpdateInstanceRequest> request = ArgumentCaptor.forClass(UpdateInstanceRequest.class);
        Mockito.verify(mockClient).updateInstance(request.capture());
        assertEquals(testConfiguration.getProperty("instanceId"), request.getValue().getInstanceId());
        return request.getValue().getBody();
    }

    private Exception updateInstanceFailure(Object body) {
        Exchange exchange = template.send("direct:update", e -> e.getMessage().setBody(body));

        assertNotNull(exchange.getException(), "updateInstance should fail");
        Mockito.verify(mockClient, Mockito.never()).updateInstance(Mockito.any());
        return assertInstanceOf(IllegalArgumentException.class, exchange.getException());
    }
}
