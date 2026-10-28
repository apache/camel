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
        return complete(destinationPath, fileOnlyName, directoryName, false);
    }

    /**
     * As {@link #completePartialRelativePath(String, String, String)}, but a relative destination path is only left as
     * it is when it starts with the directory as whole path segments: for the directory move, movement.txt.bak and
     * move-old/whatever.ext are completed (move/movement.txt.bak, move/move-old/whatever.ext), while move/backup/ and
     * move are not. Used by the remote file components, where the file name in the moveExisting expression is the name
     * of the file in its directory.
     *
     * @param  destinationPath the destination path
     * @param  fileOnlyName    the file name without the path
     * @param  directoryName   the path of the file to be moved/renamed
     * @return                 the full destination path
     * @since                  4.24
     */
    public static String completeRelativePathInDirectory(
            String destinationPath, String fileOnlyName, String directoryName) {
        return complete(destinationPath, fileOnlyName, directoryName, true);
    }

    private static String complete(
            String destinationPath, String fileOnlyName, String directoryName, boolean wholePathSegments) {

        if (destinationPath.length() > 1 && destinationPath.endsWith("/")) {
            destinationPath = destinationPath + fileOnlyName;
        }

        if (ObjectHelper.isNotEmpty(directoryName)
                && !(wholePathSegments
                        ? isInDirectory(destinationPath, directoryName) : destinationPath.startsWith(directoryName))
                && !FileUtil.isAbsolute(new File(destinationPath))) {
            destinationPath = directoryName + "/" + destinationPath;
        }

        return destinationPath;
    }

    /**
     * Whether the path is the directory itself or a path below it. The directory must match whole path segments, so
     * movement.txt.bak is not in the directory move.
     */
    private static boolean isInDirectory(String path, String directoryName) {
        if (!path.startsWith(directoryName)) {
            return false;
        }
        if (path.length() == directoryName.length() || isSeparator(directoryName.charAt(directoryName.length() - 1))) {
            return true;
        }
        return isSeparator(path.charAt(directoryName.length()));
    }

    private static boolean isSeparator(char ch) {
        return ch == '/' || ch == '\\';
    }

}
