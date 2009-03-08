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
package org.apache.camel.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File utilities
 */
public final class FileUtil {
    
    private static final int RETRY_SLEEP_MILLIS = 10;
    private static File defaultTempDir;

    private FileUtil() {
    }

    /**
     * Normalizes the path to cater for Windows and other platforms
     */
    public static String normalizePath(String path) {
        // special handling for Windows where we need to convert / to \\
        if (path != null && System.getProperty("os.name").startsWith("Windows") && path.indexOf("/") >= 0) {
            return path.replaceAll("/", "\\\\");
        }
        return path;
    }
    
    private static synchronized File getDefaultTempDir() {
        if (defaultTempDir != null
            && defaultTempDir.exists()) {
            return defaultTempDir;
        }
        
        String s = null;
        try {
            s = System.getProperty(FileUtil.class.getName() + ".TempDirectory");
        } catch (SecurityException e) {
            //Ignorable, we'll use the default
        }
        if (s == null) {
            int x = (int)(Math.random() * 1000000);
            s = System.getProperty("java.io.tmpdir");
            File checkExists = new File(s);
            if (!checkExists.exists()) {
                throw new RuntimeException("The directory " 
                                       + checkExists.getAbsolutePath() 
                                       + " does not exist, please set java.io.tempdir"
                                       + " to an existing directory");
            }
            File f = new File(s, "camel-tmp-" + x);
            while (!f.mkdir()) {
                x = (int)(Math.random() * 1000000);
                f = new File(s, "camel-tmp-" + x);
            }
            defaultTempDir = f;
            Thread hook = new Thread() {
                @Override
                public void run() {
                    removeDir(defaultTempDir);
                }
            };
            Runtime.getRuntime().addShutdownHook(hook);            
        } else {
            //assume someone outside of us will manage the directory
            File f = new File(s);
            f.mkdirs();
            defaultTempDir = f;
        }
        return defaultTempDir;
    }

    public static void mkDir(File dir) {
        if (dir == null) {
            throw new RuntimeException("dir attribute is required");
        }

        if (dir.isFile()) {
            throw new RuntimeException("Unable to create directory as a file "
                                    + "already exists with that name: " + dir.getAbsolutePath());
        }

        if (!dir.exists()) {
            boolean result = doMkDirs(dir);
            if (!result) {
                String msg = "Directory " + dir.getAbsolutePath()
                             + " creation was not successful for an unknown reason";
                throw new RuntimeException(msg);
            }
        }
    }

    /**
     * Attempt to fix possible race condition when creating directories on
     * WinXP, also Windows2000. If the mkdirs does not work, wait a little and
     * try again.
     */
    private static boolean doMkDirs(File f) {
        if (!f.mkdirs()) {
            try {
                Thread.sleep(RETRY_SLEEP_MILLIS);
                return f.mkdirs();
            } catch (InterruptedException ex) {
                return f.mkdirs();
            }
        }
        return true;
    }

    public static void removeDir(File d) {
        String[] list = d.list();
        if (list == null) {
            list = new String[0];
        }
        for (int i = 0; i < list.length; i++) {
            String s = list[i];
            File f = new File(d, s);
            if (f.isDirectory()) {
                removeDir(f);
            } else {
                delete(f);
            }
        }
        delete(d);
    }

    public static void delete(File f) {
        if (!f.delete()) {
            if (isWindows()) {
                System.gc();
            }
            try {
                Thread.sleep(RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {
                // Ignore Exception
            }
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        return osName.indexOf("windows") > -1;
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        return createTempFile(prefix, suffix, null, false);
    }
    
    public static File createTempFile(String prefix, String suffix, File parentDir,
                               boolean deleteOnExit) throws IOException {
        File result = null;
        File parent = (parentDir == null)
            ? getDefaultTempDir()
            : parentDir;
            
        if (suffix == null) {
            suffix = ".tmp";
        }
        if (prefix == null) {
            prefix = "camel";
        } else if (prefix.length() < 3) {
            prefix = prefix + "camel";
        }
        result = File.createTempFile(prefix, suffix, parent);

        //if parentDir is null, we're in our default dir
        //which will get completely wiped on exit from our exit
        //hook.  No need to set deleteOnExit() which leaks memory.
        if (deleteOnExit && parentDir != null) {
            result.deleteOnExit();
        }
        return result;
    }
    
    public static String getStringFromFile(File location) {
        InputStream is = null;
        String result = null;

        try {
            is = new FileInputStream(location);
            result = normalizeCRLF(is);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    //do nothing
                }
            }
        }

        return result;
    }

    public static String normalizeCRLF(InputStream instream) {
        BufferedReader in = new BufferedReader(new InputStreamReader(instream));
        StringBuffer result = new StringBuffer();
        String line = null;

        try {
            line = in.readLine();
            while (line != null) {
                String[] tok = line.split("\\s");

                for (int x = 0; x < tok.length; x++) {
                    String token = tok[x];
                    result.append("  " + token);
                }
                line = in.readLine();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        String rtn = result.toString();

        rtn = ignoreTokens(rtn, "<!--", "-->");
        rtn = ignoreTokens(rtn, "/*", "*/");
        return rtn;
    }
    
    private static String ignoreTokens(final String contents, 
                                       final String startToken, final String endToken) {
        String rtn = contents;
        int headerIndexStart = rtn.indexOf(startToken);
        int headerIndexEnd = rtn.indexOf(endToken);
        if (headerIndexStart != -1 && headerIndexEnd != -1 && headerIndexStart < headerIndexEnd) {
            rtn = rtn.substring(0, headerIndexStart - 1)
                + rtn.substring(headerIndexEnd + endToken.length() + 1);
        }
        return rtn;
    }

    public static List<File> getFiles(File dir, final String pattern) {
        return getFiles(dir, pattern, null);
    }
    public static List<File> getFilesRecurse(File dir, final String pattern) {
        return getFilesRecurse(dir, pattern, null);
    }

    public static List<File> getFiles(File dir, final String pattern, File exclude) {
        return getFilesRecurse(dir, Pattern.compile(pattern), exclude, false, new ArrayList<File>());
    }
    public static List<File> getFilesRecurse(File dir, final String pattern, File exclude) {
        return getFilesRecurse(dir, Pattern.compile(pattern), exclude, true, new ArrayList<File>());    
    }
    private static List<File> getFilesRecurse(File dir, 
                                              Pattern pattern,
                                              File exclude, boolean rec,
                                              List<File> fileList) {
        for (File file : dir.listFiles()) {
            if (file.equals(exclude)) {
                continue;
            }
            if (file.isDirectory() && rec) {
                getFilesRecurse(file, pattern, exclude, rec, fileList);
            } else {
                Matcher m = pattern.matcher(file.getName());
                if (m.matches()) {
                    fileList.add(file);                                
                }
            }
        }
        return fileList;
    }

    public static List<String> readLines(File file) throws Exception {
        if (!file.exists()) {
            return new ArrayList<String>();
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        List<String> results = new ArrayList<String>();
        String line = reader.readLine();
        while (line != null) {
            results.add(line);
            line = reader.readLine();
        }
        return results;
    }

    public static String stripLeadingSeparator(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith("/") || name.startsWith(File.separator)) {
            return name.substring(1);
        }
        return name;
    }

    public static String stripPath(String name) {
        if (name == null) {
            return null;
        }
        int pos = name.lastIndexOf("/");
        if (pos == -1) {
            pos = name.lastIndexOf(File.separator);
        }
        if (pos != -1) {
            return name.substring(pos + 1);
        }
        return name;
    }

}
