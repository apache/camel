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
package org.apache.camel.component.openstack.nova;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.nova.producer.KeypairProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.compute.KeypairService;
import org.openstack4j.model.compute.Keypair;
import org.openstack4j.openstack.compute.domain.NovaKeypair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeypairProducerTest extends NovaProducerTestSupport {
    private static final String KEYPAIR_NAME = "keypairName";

    @Mock
    private Keypair osTestKeypair;

    private Keypair dummyKeypair;

    @Mock
    private KeypairService keypairService;

    @Captor
    private ArgumentCaptor<String> nameCaptor;

    @Captor
    private ArgumentCaptor<String> keypairCaptor;

    @Before
    public void setUp() {
        when(computeService.keypairs()).thenReturn(keypairService);

        producer = new KeypairProducer(endpoint, client);
        dummyKeypair = createDummyKeypair();

        when(keypairService.create(anyString(), anyString())).thenReturn(osTestKeypair);
        when(keypairService.create(anyString(), isNull())).thenReturn(osTestKeypair);

        List<org.openstack4j.model.compute.Keypair> getAllList = new ArrayList<>();
        getAllList.add(osTestKeypair);
        getAllList.add(osTestKeypair);

        when(osTestKeypair.getName()).thenReturn(dummyKeypair.getName());
        when(osTestKeypair.getPublicKey()).thenReturn(dummyKeypair.getPublicKey());
    }

    @Test
    public void createKeypair() throws Exception {
        final String fingerPrint = "fp";
        final String privatecKey = "prk";
        when(osTestKeypair.getName()).thenReturn(KEYPAIR_NAME);
        when(osTestKeypair.getPublicKey()).thenReturn(dummyKeypair.getPublicKey());
        when(osTestKeypair.getFingerprint()).thenReturn(fingerPrint);
        when(osTestKeypair.getPrivateKey()).thenReturn(privatecKey);

        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, KEYPAIR_NAME);

        producer.process(exchange);

        verify(keypairService).create(nameCaptor.capture(), keypairCaptor.capture());

        assertEquals(KEYPAIR_NAME, nameCaptor.getValue());
        assertNull(keypairCaptor.getValue());

        Keypair result = msg.getBody(Keypair.class);
        assertEquals(fingerPrint, result.getFingerprint());
        assertEquals(privatecKey, result.getPrivateKey());
        assertEquals(dummyKeypair.getName(), result.getName());
        assertEquals(dummyKeypair.getPublicKey(), result.getPublicKey());

    }

    @Test
    public void createKeypairFromExisting() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, KEYPAIR_NAME);
        String key = "existing public key string";
        when(osTestKeypair.getPublicKey()).thenReturn(key);
        msg.setBody(key);

        producer.process(exchange);

        verify(keypairService).create(nameCaptor.capture(), keypairCaptor.capture());

        assertEquals(KEYPAIR_NAME, nameCaptor.getValue());
        assertEquals(key, keypairCaptor.getValue());

        Keypair result = msg.getBody(Keypair.class);
        assertEquals(dummyKeypair.getName(), result.getName());
        assertEquals(dummyKeypair.getFingerprint(), result.getFingerprint());
        assertEquals(dummyKeypair.getPrivateKey(), result.getPrivateKey());
        assertEquals(key, result.getPublicKey());
    }

    private Keypair createDummyKeypair() {
        return NovaKeypair.create(KEYPAIR_NAME, "string contains private key");
    }
}
