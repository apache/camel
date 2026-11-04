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
package org.apache.camel.component.flowable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.w3c.dom.Document;

import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.eventregistry.api.InboundEventDeserializer;
import org.flowable.eventregistry.impl.pipeline.DefaultInboundEventProcessingPipeline;
import org.flowable.eventregistry.impl.serialization.StringToXmlDocumentDeserializer;
import org.flowable.eventregistry.model.CamelInboundChannelModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FlowableInboundXmlChannelTest extends CamelFlowableTestCase {

    private String processDeploymentId;

    @BeforeEach
    public void deployEventRegistryModels() {
        eventRegistryEngineConfiguration.getEventRepositoryService().createDeployment()
                .addClasspathResource("channel/userXmlChannel.channel")
                .addClasspathResource("event/userXmlEvent.event")
                .deploy();
        processDeploymentId = deployProcessDefinition("process/startXml.bpmn20.xml");
    }

    @AfterEach
    public void deleteProcessDeployment() {
        repositoryService.deleteDeployment(processDeploymentId, true);
    }

    @Test
    public void xmlEventStartsProcessWithMappedVariables() {
        template.sendBody("direct:startXml", "<user><name>John Doe</name><age>23</age></user>");

        ProcessInstance processInstance = startedProcessInstance();
        assertEquals("John Doe", runtimeService.getVariable(processInstance.getId(), "name"));
        assertEquals(23, runtimeService.getVariable(processInstance.getId(), "age"));
    }

    @Test
    public void xmlChannelUsesCamelXmlDeserializer() {
        template.sendBody("direct:startXml", "<user><name>John Doe</name><age>23</age></user>");

        assertInstanceOf(CamelStringToXmlDocumentDeserializer.class, xmlPipeline().getInboundEventDeserializer());
    }

    @Test
    public void customXmlDeserializerIsKept() {
        InboundEventDeserializer<Document> custom = new StringToXmlDocumentDeserializer() {
        };
        xmlPipeline().setInboundEventDeserializer(custom);

        template.sendBody("direct:startXml", "<user><name>John Doe</name><age>23</age></user>");

        assertSame(custom, xmlPipeline().getInboundEventDeserializer());
        assertEquals("John Doe", runtimeService.getVariable(startedProcessInstance().getId(), "name"));
    }

    @Test
    public void entityDeclaredInDoctypeIsNotExpandedFromFile() throws Exception {
        Path file = Path.of("target", "flowable-xml-channel", "entity.txt").toAbsolutePath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content-of-the-entity-file", StandardCharsets.UTF_8);

        template.sendBody("direct:startXml",
                "<?xml version=\"1.0\"?><!DOCTYPE event [<!ENTITY content SYSTEM \"" + file.toUri() + "\">]>"
                                             + "<name>&content;</name>");

        Object name = runtimeService.getVariable(startedProcessInstance().getId(), "name");
        assertFalse(name != null && name.toString().contains("content-of-the-entity-file"),
                "The entity declared in the DOCTYPE must not be expanded from the file, but the variable is: " + name);
    }

    private ProcessInstance startedProcessInstance() {
        ProcessInstance processInstance
                = runtimeService.createProcessInstanceQuery().processDefinitionKey("camelXmlProcess").singleResult();
        assertNotNull(processInstance);
        return processInstance;
    }

    @SuppressWarnings("unchecked")
    private DefaultInboundEventProcessingPipeline<Document> xmlPipeline() {
        CamelInboundChannelModel channel
                = context.getEndpoint("flowable:userXmlChannel", FlowableEndpoint.class).getCamelInboundChannel();
        return (DefaultInboundEventProcessingPipeline<Document>) channel.getInboundEventProcessingPipeline();
    }
}
