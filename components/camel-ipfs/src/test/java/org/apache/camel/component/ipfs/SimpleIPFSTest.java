/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.apache.camel.component.ipfs;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import io.nessus.utils.StreamUtils;

public class SimpleIPFSTest {

    @Test
    public void ipfsVersion() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("ipfs:version");
            }
        });

        camelctx.start();
        assumeIPFS(camelctx);
        
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            String result = producer.requestBody("direct:start", null, String.class);
            Assert.assertTrue(result, result.startsWith("0.4"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsAddSingle() throws Exception {

        String HASH = "QmYgjSRbXFPdPYKqQSnUjmXLYLudVahEJQotMaAJKt6Lbd";
        
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
            Assert.assertEquals(HASH, res);
        } finally {
            camelctx.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ipfsAddRecursive() throws Exception {

        String HASH = "Qme6hd6tYXTFb7bb7L3JZ5U6ygktpAHKxbaeffYyQN85mW";
        
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
            Assert.assertEquals(HASH, res.get(9));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsCat() throws Exception {

        String HASH = "QmUD7uG5prAMHbcCfp4x1G1mMSpywcSMHTGpq62sbpDAg6";
        
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
            InputStream res = producer.requestBody("direct:start", HASH, InputStream.class);
            verifyFileContent(res);
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsGetSingle() throws Exception {

        String HASH = "QmUD7uG5prAMHbcCfp4x1G1mMSpywcSMHTGpq62sbpDAg6";
        
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
            Path res = producer.requestBody("direct:start", HASH, Path.class);
            Assert.assertEquals(Paths.get("target", HASH), res);
            verifyFileContent(new FileInputStream(res.toFile()));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void ipfsGetRecursive() throws Exception {

        String HASH = "Qme6hd6tYXTFb7bb7L3JZ5U6ygktpAHKxbaeffYyQN85mW";
        
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
            Path res = producer.requestBody("direct:start", HASH, Path.class);
            Assert.assertEquals(Paths.get("target", HASH), res);
            Assert.assertTrue(res.toFile().isDirectory());
            Assert.assertTrue(res.resolve("index.html").toFile().exists());
        } finally {
            camelctx.stop();
        }
    }

    private void verifyFileContent(InputStream ins) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copyStream(ins, baos);
        Assert.assertEquals("The quick brown fox jumps over the lazy dog.", new String (baos.toByteArray()));
    }

    private void assumeIPFS(CamelContext camelctx) {
        IPFSComponent comp = camelctx.getComponent("ipfs", IPFSComponent.class);
        Assume.assumeTrue(comp.getIPFSClient().hasConnection());
    }
}