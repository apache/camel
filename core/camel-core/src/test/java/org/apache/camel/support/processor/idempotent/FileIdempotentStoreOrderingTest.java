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
package org.apache.camel.support.processor.idempotent;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.TestSupport.createDirectory;
import static org.apache.camel.TestSupport.deleteDirectory;

public class FileIdempotentStoreOrderingTest {

    private FileIdempotentRepository fileIdempotentRepository;
    private List<String> files;

    @Before
    public void setup() {
        files = Arrays.asList("file1.txt.20171123", "file2.txt.20171123", "file1.txt.20171124", "file3.txt.20171125", "file2.txt.20171126", "fixed.income.lamr.out.20171126",
                              "pricing.px.20171126", "test.out.20171126", "processing.source.lamr.out.20171126");
        this.fileIdempotentRepository = new FileIdempotentRepository();
    }

    @Test
    public void testTrunkStoreNotMaxHit() throws Exception {
        // ensure empty folder
        deleteDirectory("target/data/mystore");
        createDirectory("target/data/mystore");

        // given
        File fileStore = new File("target/data/mystore/data.dat");
        fileIdempotentRepository.setFileStore(fileStore);
        fileIdempotentRepository.setCacheSize(10);
        fileIdempotentRepository.start();
        files.forEach(e -> fileIdempotentRepository.add(e));

        // when (will rebalance)
        fileIdempotentRepository.stop();

        // then
        Stream<String> fileContent = Files.lines(fileStore.toPath());
        List<String> fileEntries = fileContent.collect(Collectors.toList());
        fileContent.close();
        // expected order
        Assert.assertThat(fileEntries,
                          IsIterableContainingInOrder.contains("file1.txt.20171123", "file2.txt.20171123", "file1.txt.20171124", "file3.txt.20171125", "file2.txt.20171126",
                                                               "fixed.income.lamr.out.20171126", "pricing.px.20171126", "test.out.20171126",
                                                               "processing.source.lamr.out.20171126"));
    }

    @Test
    public void testTrunkStoreFirstLevelMaxHit() throws Exception {
        // ensure empty folder
        deleteDirectory("target/data/mystore");
        createDirectory("target/data/mystore");

        // given
        File fileStore = new File("target/data/mystore/data.dat");
        fileIdempotentRepository.setFileStore(fileStore);
        fileIdempotentRepository.setCacheSize(5);
        fileIdempotentRepository.start();
        files.forEach(e -> fileIdempotentRepository.add(e));

        // when (will rebalance)
        fileIdempotentRepository.stop();

        // then
        Stream<String> fileContent = Files.lines(fileStore.toPath());
        List<String> fileEntries = fileContent.collect(Collectors.toList());
        fileContent.close();
        // expected order
        Assert.assertThat(fileEntries,
                          IsIterableContainingInOrder.contains("file1.txt.20171123", "file2.txt.20171123", "file1.txt.20171124", "file3.txt.20171125", "file2.txt.20171126",
                                                               "fixed.income.lamr.out.20171126", "pricing.px.20171126", "test.out.20171126",
                                                               "processing.source.lamr.out.20171126"));
    }

    @Test
    public void testTrunkStoreFileMaxHit() throws Exception {
        // ensure empty folder
        deleteDirectory("target/data/mystore");
        createDirectory("target/data/mystore");

        // given
        File fileStore = new File("target/data/mystore/data.dat");
        fileIdempotentRepository.setFileStore(fileStore);
        fileIdempotentRepository.setCacheSize(5);
        fileIdempotentRepository.setMaxFileStoreSize(128);
        fileIdempotentRepository.setDropOldestFileStore(1000);

        fileIdempotentRepository.start();
        files.forEach(e -> fileIdempotentRepository.add(e));

        // force cleanup and trunk
        fileIdempotentRepository.cleanup();
        fileIdempotentRepository.trunkStore();

        fileIdempotentRepository.stop();

        // then
        Stream<String> fileContent = Files.lines(fileStore.toPath());
        List<String> fileEntries = fileContent.collect(Collectors.toList());
        fileContent.close();

        // all old entries is removed
        Assert.assertEquals(0, fileEntries.size());
    }

}
