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
package org.apache.camel.component.ipfs;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


import io.nessus.ipfs.client.DefaultIPFSClient;
import io.nessus.ipfs.client.IPFSClient;
import io.nessus.ipfs.client.IPFSException;
import io.nessus.utils.StreamUtils;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SimpleIPFSTest {

    IPFSClient ipfs;
    
    @Before
    public void before() {
        ipfs = new DefaultIPFSClient("127.0.0.1", 5001);
        try {
            ipfs.connect();
        } catch (IPFSException ex) {
            // ignore
        }
        Assume.assumeTrue(ipfs.hasConnection());
    }
    
    @Test
    public void ipfsVersion() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startA").to("ipfs:version");
                from("direct:startB").to("ipfs:127.0.0.1/version");
                from("direct:startC").to("ipfs:127.0.0.1:5001/version");
            }
        });

        camelctx.start();
        assumeIPFS(camelctx);

        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            String resA = producer.requestBody("direct:startA", null, String.class);
            String resB = producer.requestBody("direct:startB", null, String.class);
            String resC = producer.requestBody("direct:startC", null, String.class);
            Arrays.asList(resA, resB, resC).forEach(res -> {
                Assert.assertTrue("Expecting 0.4 in: " + resA, resA.startsWith("0.4"));
            });
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsAddSingle() throws Exception {

        String hash = "QmYgjSRbXFPdPYKqQSnUjmXLYLudVahEJQotMaAJKt6Lbd";

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ipfs:add");
            }
        });

        Path path = Paths.get("src/test/resources/html/index.html");

        camelctx.start();
        assumeIPFS(camelctx);

        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            String res = producer.requestBody("direct:start", path, String.class);
            Assert.assertEquals(hash, res);
        } finally {
            camelctx.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ipfsAddRecursive() throws Exception {

        String hash = "Qme6hd6tYXTFb7bb7L3JZ5U6ygktpAHKxbaeffYyQN85mW";

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ipfs:add");
            }
        });

        Path path = Paths.get("src/test/resources/html");

        camelctx.start();
        assumeIPFS(camelctx);

        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            List<String> res = producer.requestBody("direct:start", path, List.class);
            Assert.assertEquals(10, res.size());
            Assert.assertEquals(hash, res.get(9));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsCat() throws Exception {

        String hash = "QmUD7uG5prAMHbcCfp4x1G1mMSpywcSMHTGpq62sbpDAg6";

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ipfs:cat");
            }
        });

        camelctx.start();
        assumeIPFS(camelctx);

        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            InputStream res = producer.requestBody("direct:start", hash, InputStream.class);
            verifyFileContent(res);
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsGetSingle() throws Exception {

        String hash = "QmUD7uG5prAMHbcCfp4x1G1mMSpywcSMHTGpq62sbpDAg6";

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ipfs:get?outdir=target");
            }
        });

        camelctx.start();
        assumeIPFS(camelctx);

        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Path res = producer.requestBody("direct:start", hash, Path.class);
            Assert.assertEquals(Paths.get("target", hash), res);
            verifyFileContent(new FileInputStream(res.toFile()));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsGetRecursive() throws Exception {

        String hash = "Qme6hd6tYXTFb7bb7L3JZ5U6ygktpAHKxbaeffYyQN85mW";

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ipfs:get?outdir=target");
            }
        });

        camelctx.start();
        assumeIPFS(camelctx);

        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Path res = producer.requestBody("direct:start", hash, Path.class);
            Assert.assertEquals(Paths.get("target", hash), res);
            Assert.assertTrue(res.toFile().isDirectory());
            Assert.assertTrue(res.resolve("index.html").toFile().exists());
        } finally {
            camelctx.stop();
        }
    }

    private void verifyFileContent(InputStream ins) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copyStream(ins, baos);
        Assert.assertEquals("The quick brown fox jumps over the lazy dog.", new String(baos.toByteArray()));
    }

    private void assumeIPFS(CamelContext camelctx) {
        IPFSComponent comp = camelctx.getComponent("ipfs", IPFSComponent.class);
        Assume.assumeTrue(comp.getIPFSClient().hasConnection());
    }
}
