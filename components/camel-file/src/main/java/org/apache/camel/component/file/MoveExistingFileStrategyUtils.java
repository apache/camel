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

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

public final class MoveExistingFileStrategyUtils {

    private MoveExistingFileStrategyUtils() {
    }

    /**
     * This method manipulates the destinationPath in case of moveExisting parameter is expressed as file language
     * expression subdirectory name of the directoryName adding directoryName on top and file name at the end.
     *
     * for example, a camel endpoint like that:
     *
     * file://data/file?fileExist=Move&moveExisting=archive-${date:now:yyyyMMddHHmmssSSS}/
     *
     * directoryName = data/file, fileOnlyName = whatever.ext, destinationPath = archive-20201110115125770
     *
     * the outcome of this method would be data/file/archive-20201110115125770/whatever.ext
     *
     * @param  destinationPath the destination path
     * @param  fileOnlyName    the file name without the path
     * @param  directoryName   the path of the file to be moved/renamed
     * @return                 the full destination path
     */
    public static String completePartialRelativePath(String destinationPath, String fileOnlyName, String directoryName) {

        if (destinationPath.length() > 1 && destinationPath.endsWith("/")) {
            destinationPath = destinationPath + fileOnlyName;
        }

        if (ObjectHelper.isNotEmpty(directoryName) && !destinationPath.startsWith(directoryName)
                && !FileUtil.isAbsolute(new File(destinationPath))) {
            destinationPath = directoryName + "/" + destinationPath;
        }

        return destinationPath;
    }

}
