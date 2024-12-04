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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.File;

import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.util.FileUtil;

public final class CommandHelper {

    private static ThreadLocal<Printer> printerAssociation = new ThreadLocal<>();

    private CommandHelper() {
    }

    public static Printer GetPrinter() {
        return printerAssociation.get();
    }

    public static void SetPrinter(Printer out) {
        printerAssociation.set(out);
    }

    public static void cleanExportDir(String dir) {
        CommandHelper.cleanExportDir(dir, true);
    }

    public static void cleanExportDir(String dir, boolean keepHidden) {
        File target = new File(dir);
        File[] files = target.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && (!keepHidden || !f.isHidden())) {
                    FileUtil.removeDir(f);
                } else if (f.isFile() && (!keepHidden || !f.isHidden())) {
                    FileUtil.deleteFile(f);
                }
            }
        }
    }
}
