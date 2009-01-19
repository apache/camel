package org.apache.camel.example.server;

import org.apache.camel.spring.Main;
import org.springframework.stereotype.Service;

/**
 * Service for stopping the server.
 */
@Service(value = "shutdown")
public class GracefulShutdownService {

    public void shutdown(String payload) throws Exception {
        System.out.println("Stopping Server as we recieved a " + payload + " command");
        Main.getInstance().stop();
    }
}
