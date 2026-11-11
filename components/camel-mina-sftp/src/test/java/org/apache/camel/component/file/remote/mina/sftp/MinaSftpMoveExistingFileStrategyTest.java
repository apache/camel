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
package org.apache.camel.component.file.remote.mina.sftp;

import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.remote.SftpDefaultMoveExistingFileStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * fileExist=Move with the path of the file from the login directory (stepwise=false, a tempFileName, mina-sftp): a
 * relative moveExisting is put in the directory of the file, also when it starts with the name of that directory.
 */
class MinaSftpMoveExistingFileStrategyTest extends CamelTestSupport {

    @ParameterizedTest
    @CsvSource({
            "move/movement.txt, ${file:name}.bak, move/movement.txt.bak",
            "move/hello.txt, ${file:name}.bak, move/hello.txt.bak",
            "move/hello.txt, backup/${file:name}, move/backup/hello.txt",
            "move/hello.txt, move-old/${file:name}, move/move-old/hello.txt",
            "move/hello.txt, ${file:parent}/renamed-${file:onlyname}, move/renamed-hello.txt",
            "move/hello.txt, ${file:parent}-old/${file:onlyname}, move/move-old/hello.txt",
            "inbox/orders/hello.txt, backup/${file:name}, inbox/orders/backup/hello.txt" })
    void moveExistingIsInTheDirectoryOfTheFile(String fileName, String moveExisting, String expected) {
        GenericFileEndpoint<?> endpoint = context.getEndpoint(
                "mina-sftp://localhost/move?fileExist=Move&moveExisting=" + moveExisting, GenericFileEndpoint.class);
        GenericFileOperations<?> operations = mock(GenericFileOperations.class);
        when(operations.renameFile(anyString(), anyString())).thenReturn(true);

        new SftpDefaultMoveExistingFileStrategy().moveExistingFile(endpoint, operations, fileName);

        verify(operations).renameFile(fileName, expected);
    }
}
