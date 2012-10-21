package ${package};

import org.apache.camel.main.Main;

/**
 * A Camel Application
 */
public class MainApp {

    /**
     * A main() so we can easily run these routing rules in our IDE
     */
    public static void main(String... args) throws Exception {

        System.out.println("\n\n\n\n");
        System.out.println("===============================================");
        System.out.println("Open your web browser on http://localhost:8080");
        System.out.println("Press ctrl+c to stop this example");
        System.out.println("===============================================");
        System.out.println("\n\n\n\n");

        Main main = new Main();
        main.enableHangupSupport();
        main.addRouteBuilder(new MyRouteBuilder());
        main.run(args);
    }

}

