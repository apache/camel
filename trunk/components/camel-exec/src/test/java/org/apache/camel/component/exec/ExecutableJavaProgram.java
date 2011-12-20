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
package org.apache.camel.component.exec;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

/**
 * A test Java main class to be executed. The behavior of the program is
 * controlled by the arguments that the {@link #main(String[])} receives. Valid
 * arguments are the public static fields of the class.
 */
public class ExecutableJavaProgram {
    /**
     * Start 2 threads that print text in the stdout and stderr, each printing
     * {@link #LINES_TO_PRINT_FROM_EACH_THREAD} lines.
     */
    public static final String THREADS = "threads";

    public static final String SLEEP_WITH_TIMEOUT = "timeout";

    public static final int SLEEP_TIME = 60 * 1000;

    public static final String PRINT_IN_STDOUT = "print.in.stdout";

    public static final String PRINT_ARGS_STDOUT = "print.args.stdout";

    public static final String PRINT_IN_STDERR = "print.in.stderr";

    public static final String READ_INPUT_LINES_AND_PRINT_THEM = "read.input.lines.and.print.them";

    public static final int EXIT_WITH_VALUE_0 = 0;

    public static final int EXIT_WITH_VALUE_1 = 1;

    public static final int LINES_TO_PRINT_FROM_EACH_THREAD = 50;

    protected ExecutableJavaProgram() {

    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Empty args are not allowed.");
        }

        if (args[0].equals(PRINT_IN_STDOUT)) {
            System.out.print(PRINT_IN_STDOUT);
            System.exit(0);
        } else if (args[0].equals(PRINT_ARGS_STDOUT)) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                System.out.println(i + arg);
            }
            System.exit(0);
        } else if (args[0].equals(PRINT_IN_STDERR)) {
            System.err.print(PRINT_IN_STDERR);
            System.exit(1);
        } else if (args[0].equals(String.valueOf(EXIT_WITH_VALUE_0))) {
            System.exit(0);
        } else if (args[0].equals(String.valueOf(EXIT_WITH_VALUE_1))) {
            System.exit(1);
        } else if (args[0].equals(THREADS)) {
            Thread stderrPrinterThread = new Thread(new ErrPrinter());
            Thread stdoutPrinterThread = new Thread(new OutPrinter());

            stderrPrinterThread.start();
            stdoutPrinterThread.start();
            stderrPrinterThread.join();
            stdoutPrinterThread.join();

        } else if (args[0].equals(SLEEP_WITH_TIMEOUT)) {
            doSleep();
            System.exit(0);
        } else if (READ_INPUT_LINES_AND_PRINT_THEM.equals(args[0])) {
            LineIterator iterator = IOUtils.lineIterator(System.in, "UTF-8");
            while (iterator.hasNext()) {
                String line = iterator.nextLine();
                System.out.println(line);

            }
        } else {
            System.out.println(args[0]);
        }

    }

    private static void doSleep() throws InterruptedException {
        int sleepInterval = 50;
        // Note, that sleeping in the main thread prevents the process from
        // being destroyed for that time. The process is killed namely when
        // sleep returns(observed on Windows XP)
        int t = 0;
        System.out.println("Sleeping every " + String.valueOf(sleepInterval) + " ms");
        for (; t < SLEEP_TIME % sleepInterval; t += sleepInterval) {
            Thread.sleep(sleepInterval);
        }

    }

    private static class ErrPrinter implements Runnable {
        public void run() {
            for (int t = 0; t < LINES_TO_PRINT_FROM_EACH_THREAD; t++) {
                System.err.println(PRINT_IN_STDERR);
            }
        }
    }

    private static class OutPrinter implements Runnable {
        public void run() {
            for (int t = 0; t < LINES_TO_PRINT_FROM_EACH_THREAD; t++) {
                System.out.println(PRINT_IN_STDOUT);
            }
        }
    }
}
