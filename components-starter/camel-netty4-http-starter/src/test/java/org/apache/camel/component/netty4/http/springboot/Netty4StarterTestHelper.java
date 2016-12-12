package org.apache.camel.component.netty4.http.springboot;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.AvailablePortFinder;

public final class Netty4StarterTestHelper {

    private static volatile int port = -1;

    private static void initPort() throws Exception {
        if (port <= 0) {
            File file = new File("target/nettyport.txt");

            if (!file.exists()) {
                // start from somewhere in the 26xxx range
                port = AvailablePortFinder.getNextAvailable(26000);
            } else {
                // read port number from file
                String s = IOConverter.toString(file, null);
                port = Integer.parseInt(s);
                // use next free port
                port = AvailablePortFinder.getNextAvailable(port + 1);
            }

            // save to file, do not append
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(String.valueOf(port).getBytes());
            }
        }
    }

    public static int getPort() {
        try {
            initPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return port;
    }
}
