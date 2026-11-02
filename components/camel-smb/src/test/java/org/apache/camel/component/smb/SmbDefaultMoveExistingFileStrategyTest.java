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
package org.apache.camel.component.smb;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * fileExist=Move: the producer gives the path of the file from the share, and a relative moveExisting is put in the
 * directory of the file, with ${file:name} the name of the file in that directory.
 */
class SmbDefaultMoveExistingFileStrategyTest extends CamelTestSupport {

    @ParameterizedTest
    @CsvSource({
            "move/hello.txt, backup/${file:name}, move/backup/hello.txt",
            "move/hello.txt, old-${file:name}, move/old-hello.txt",
            "move/movement.txt, ${file:name}.bak, move/movement.txt.bak",
            "move/hello.txt, ${file:parent}/renamed-${file:onlyname}, move/renamed-hello.txt",
            "move/hello.txt, ${file:parent}-old/${file:onlyname}, move/move-old/hello.txt",
            "move/hello.txt, ../move-old/${file:onlyname}, move-old/hello.txt",
            "move/hello.txt, old-${file:onlyname}, move/old-hello.txt",
            "inbox/orders/hello.txt, backup/${file:name.noext}.bak, inbox/orders/backup/hello.bak" })
    void moveExistingIsInTheDirectoryOfTheFile(String fileName, String moveExisting, String expected) {
        GenericFileEndpoint<?> endpoint = context.getEndpoint(
                "smb:localhost/share/move?fileExist=Move&moveExisting=" + moveExisting, GenericFileEndpoint.class);
        List<String> renames = new ArrayList<>();
        GenericFileOperations<?> operations = (GenericFileOperations<?>) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { GenericFileOperations.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsFile" -> false;
                    case "buildDirectory" -> true;
                    case "renameFile" -> renames.add(args[0] + " -> " + args[1]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        new SmbDefaultMoveExistingFileStrategy().moveExistingFile(endpoint, operations, fileName);

        assertEquals(List.of(fileName + " -> " + expected), renames);
    }
}
