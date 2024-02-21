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
package org.apache.camel.component.file;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.strategy.FileMoveExistingStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FileProducerMoveExistingStrategyTest extends ContextTestSupport {

    private MyStrategy myStrategy = new MyStrategy();

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myStrategy", myStrategy);
        return jndi;
    }

    @Test
    public void testExistingFileExists() throws Exception {
        template.sendBodyAndHeader(
                fileUri("?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&moveExistingFileStrategy=#myStrategy"),
                "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(
                fileUri("?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&moveExistingFileStrategy=#myStrategy"),
                "Bye Existing World 1", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(
                fileUri("?fileExist=Move&moveExisting=${file:parent}/renamed-${file:onlyname}&moveExistingFileStrategy=#myStrategy"),
                "Bye Existing World 2", Exchange.FILE_NAME, "hello.txt");

        assertFileExists(testFile("hello.txt"), "Bye Existing World 2");

        assertFileExists(testFile("renamed-hello2.txt"), "Bye Existing World 1");

        assertFileExists(testFile("renamed-hello1.txt"), "Hello World");
    }

    private static class MyStrategy implements FileMoveExistingStrategy {

        private static final Logger LOG = LoggerFactory.getLogger(FileMoveExistingStrategy.class);
        private int counter;

        @Override
        public boolean moveExistingFile(GenericFileEndpoint<?> endpoint, GenericFileOperations<?> operations, String fileName)
                throws GenericFileOperationFailedException {

            // need to evaluate using a dummy and simulate the file first, to
            // have access to all the file attributes
            // create a dummy exchange as Exchange is needed for expression
            // evaluation
            // we support only the following 3 tokens.
            Exchange dummy = endpoint.createExchange();
            String parent = FileUtil.onlyPath(fileName);
            String onlyName = FileUtil.stripPath(fileName);

            dummy.getIn().setHeader(Exchange.FILE_NAME, fileName);
            dummy.getIn().setHeader(Exchange.FILE_NAME_ONLY, onlyName);
            dummy.getIn().setHeader(Exchange.FILE_PARENT, parent);

            String to = endpoint.getMoveExisting().evaluate(dummy, String.class);
            counter++;
            String fileNameWithoutExtension = to.substring(0, to.lastIndexOf('.')) + "" + counter;
            to = fileNameWithoutExtension + to.substring(to.lastIndexOf('.'), to.length());
            // we must normalize it (to avoid having both \ and / in the name
            // which confuses java.io.File)
            to = FileUtil.normalizePath(to);
            if (ObjectHelper.isEmpty(to)) {
                throw new GenericFileOperationFailedException(
                        "moveExisting evaluated as empty String, cannot move existing file: " + fileName);
            }

            // ensure any paths is created before we rename as the renamed file
            // may be in a different path (which may be non exiting)
            // use java.io.File to compute the file path
            File toFile = new File(to);
            String directory = toFile.getParent();
            boolean absolute = FileUtil.isAbsolute(toFile);
            if (directory != null) {
                if (!operations.buildDirectory(directory, absolute)) {
                    LOG.debug("Cannot build directory [{}] (could be because of denied permissions)", directory);
                }
            }

            // deal if there already exists a file
            if (operations.existsFile(to)) {
                if (endpoint.isEagerDeleteTargetFile()) {
                    LOG.trace("Deleting existing file: {}", to);
                    if (!operations.deleteFile(to)) {
                        throw new GenericFileOperationFailedException("Cannot delete file: " + to);
                    }
                } else {
                    throw new GenericFileOperationFailedException(
                            "Cannot moved existing file from: " + fileName + " to: " + to + " as there already exists a file: "
                                                                  + to);
                }
            }

            LOG.trace("Moving existing file: {} to: {}", fileName, to);
            if (!operations.renameFile(fileName, to)) {
                throw new GenericFileOperationFailedException("Cannot rename file from: " + fileName + " to: " + to);
            }
            return true;
        }

    }

}
