package org.apache.camel.graalvm;

import org.apache.camel.CamelContext;

public class Main extends org.apache.camel.main.Main {

    @Override
    protected CamelContext createContext() {
        return new FastCamelContext(registry);
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        instance = main;
        main.run(args);
        System.exit(main.getExitCode());
    }

}
