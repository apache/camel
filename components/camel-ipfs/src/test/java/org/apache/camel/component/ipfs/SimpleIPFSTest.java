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
package org.apache.camel.component.ipfs;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.nessus.utils.StreamUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/*

> ipfs config Addresses.Gateway /ip4/127.0.0.1/tcp/8088

> ipfs daemon

Initializing daemon...
go-ipfs version: 0.4.22-
Golang version: go1.12.7
...
API server listening on /ip4/127.0.0.1/tcp/5001
WebUI: http://webui
Gateway (readonly) server listening on /ip4/127.0.0.1/tcp/8088
Daemon is ready

*/

public class SimpleIPFSTest {

    private static final String SINGLE_HASH = "QmUD7uG5prAMHbcCfp4x1G1mMSpywcSMHTGpq62sbpDAg6";
    private static final String RECURSIVE_HASH = "QmdcE2PmF5SBGCs1EVtznNTFPu4GoJztgJmAvdq66XxM3h";

    @Test
    public void ipfsVersion() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:startA").to("ipfs:version");
                }
            });

            camelctx.start();

            try {
                ProducerTemplate producer = camelctx.createProducerTemplate();
                String resA = producer.requestBody("direct:startA", null, String.class);
                Assert.assertTrue("Expecting 0.4 in: " + resA, resA.startsWith("0.4"));
            } catch (Exception e) {
                boolean notRunning = e.getCause().getMessage().contains("Is IPFS running");
                Assume.assumeFalse("IPFS is running", notRunning);
            }
        }
    }

    @Test
    public void ipfsAddSingle() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("ipfs:add");
                }
            });

            camelctx.start();

            try {
                Path path = Paths.get("src/test/resources/html/etc/userfile.txt");
                ProducerTemplate producer = camelctx.createProducerTemplate();
                String res = producer.requestBody("direct:start", path, String.class);
                Assert.assertEquals(SINGLE_HASH, res);
            } catch (Exception e) {
                boolean notRunning = e.getCause().getMessage().contains("Is IPFS running");
                Assume.assumeFalse("IPFS is running", notRunning);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ipfsAddRecursive() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("ipfs:add");
                }
            });

            camelctx.start();

            try {
                Path path = Paths.get("src/test/resources/html");
                ProducerTemplate producer = camelctx.createProducerTemplate();
                List<String> res = producer.requestBody("direct:start", path, List.class);
                Assert.assertEquals(10, res.size());
                Assert.assertEquals(RECURSIVE_HASH, res.get(9));
            } catch (Exception e) {
                boolean notRunning = e.getCause().getMessage().contains("Is IPFS running");
                Assume.assumeFalse("IPFS is running", notRunning);
            }
        }
    }

    @Test
    public void ipfsCat() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("ipfs:cat");
                }
            });

            camelctx.start();

            try {
                ProducerTemplate producer = camelctx.createProducerTemplate();
                InputStream res = producer.requestBody("direct:start", SINGLE_HASH, InputStream.class);
                verifyFileContent(res);
            } catch (Exception e) {
                boolean notRunning = e.getCause().getMessage().contains("Is IPFS running");
                Assume.assumeFalse("IPFS is running", notRunning);
            }
        }
    }

    @Test
    public void ipfsGetSingle() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("ipfs:get?outdir=target");
                }
            });

            camelctx.start();

            try {
                ProducerTemplate producer = camelctx.createProducerTemplate();
                Path res = producer.requestBody("direct:start", SINGLE_HASH, Path.class);
                Assert.assertEquals(Paths.get("target", SINGLE_HASH), res);
                verifyFileContent(new FileInputStream(res.toFile()));
            } catch (Exception e) {
                boolean notRunning = e.getCause().getMessage().contains("Is IPFS running");
                Assume.assumeFalse("IPFS is running", notRunning);
            }
        }
    }

    @Test
    public void ipfsGetRecursive() throws Exception {

        try (CamelContext camelctx = new DefaultCamelContext()) {

            camelctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("ipfs:get?outdir=target");
                }
            });

            camelctx.start();

            try {
                ProducerTemplate producer = camelctx.createProducerTemplate();
                Path res = producer.requestBody("direct:start", RECURSIVE_HASH, Path.class);
                Assert.assertEquals(Paths.get("target", RECURSIVE_HASH), res);
                Assert.assertTrue(res.toFile().isDirectory());
                Assert.assertTrue(res.resolve("index.html").toFile().exists());
            } catch (Exception e) {
                boolean notRunning = e.getCause().getMessage().contains("Is IPFS running");
                Assume.assumeFalse("IPFS is running", notRunning);
            }
        }
    }

    private void verifyFileContent(InputStream ins) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copyStream(ins, baos);
        Assert.assertEquals("The quick brown fox jumps over the lazy dog.", new String(baos.toByteArray()));
    }

}
