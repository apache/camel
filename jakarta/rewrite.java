///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0

import java.io.IOError;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "rewrite")
class rewrite implements Callable<Integer> {

    public static void main(String... args) throws Exception {
        int exitCode = new CommandLine(new rewrite()).execute(args);
        System.exit(exitCode);
    }

        
    @Override
    public Integer call() throws Exception {
        // Rebase on top of main
        // exec("git", "checkout", "jakarta-rewrite");
        // Create/reset branch 'rewritten'
        // exec("git", "checkout", "-B", "jakarta-rewritten", "jakarta-rewrite");

        // Switch version to 4.0.0-SNAPSHOT
        System.out.println("Switch version to 4.0.0-SNAPSHOT");
        try (Stream<Path> files = Files.walk(Paths.get("."))) {
            files.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.equals("pom.xml")) {
                    regex(p, "<version>3\\.20\\.0-SNAPSHOT</version>", "<version>4.0.0-SNAPSHOT</version>");
                } else if (p.toString().contains("/src/generated/resources/")) {
                    if (name.endsWith(".properties")) {
                        regex(p, "version=3.20.0-SNAPSHOT", "version=4.0.0-SNAPSHOT");
                    } else if (name.endsWith(".json")) {
                        regex(p, "\"version\": \"3\\.20\\.0-SNAPSHOT\"", "\"version\": \"4.0.0-SNAPSHOT\"");
                    }
                }
            });
        }

        // Remove OSGi support
        System.out.println("Remove OSGi support");
        try (Stream<Path> files = Files.walk(Paths.get("."))) {
            files
                .filter(p -> p.getFileName().toString().equals("pom.xml"))
                .forEach(p -> regex(p, 
                        " *<([^>]+-version-range)>[^<]+</\\1> *\n", "",
                        " *<(camel\\.osgi\\.[^>]+)(>[^<]+</\\1>| *\\/>) *\n", "",
                        " *<!-- OSGi bundles properties --> *\n", "",
                        " *<(plugin|pluginExecutionFilter)>\\s*<groupId>org.apache.camel</groupId>\\s*<artifactId>camel-bundle-plugin</artifactId>[\\s\\S]*?</\\1> *\n", "",
                        " *<module>init</module> *\n", ""
                ));
        }
        regex(Paths.get("components/camel-jasypt/pom.xml"), "<manifestFile>.*?</manifestFile>\\s*", "");

        // Switch to javax.annotation.processing.Generated
        System.out.println("Switch to javax.annotation.processing.Generated");
        try (Stream<Path> files = Files.walk(Paths.get("."))) {
            files
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".java") || name.endsWith(".txt") || name.endsWith(".vm");
                })
                .forEach(p -> regex(p, 
                    "javax.annotation.Generated", "javax.annotation.processing.Generated"
                ));
        }

        // Add support for jakarta in checkstyle
        System.out.println("Add support for jakarta in checkstyle");
        try (Stream<Path> files = Stream.of(
                            "pom.xml", 
                            "etc/pom.xml", 
                            "buildingtools/src/main/resources/camel-checkstyle.xml").map(Paths::get)) {
            files.forEach(p -> regex(p, 
                    "java;javax;org.w3c;org.xml;w3c;", "java;jakarta;javax;org.w3c;org.xml;w3c;",
                    "java,javax,org.w3c,org.xml,junit", "java,jakarta,javax,org.w3c,org.xml,junit"
            ));
        }

        // Switch javax packages to jakarta
        System.out.println("Switch javax packages to jakarta");
        try (Stream<Path> files = Files.walk(Paths.get("."))) {
            files
            .filter(p ->
                    p.getFileName().toString().endsWith(".java") && !p.toString().equals("jakarta/rewrite.java")
                || p.getFileName().toString().endsWith(".json") && p.toString().contains("/src/generated/resources/"))
            .forEach(p -> regex(p, 
                "javax\\.activation", "jakarta\\.activation",
                "javax\\.annotation\\.security", "jakarta\\.annotation\\.security",
                "javax\\.annotation\\.sql", "jakarta\\.annotation\\.sql",
                "javax\\.annotation(?!\\.processing)", "jakarta\\.annotation",
                "javax\\.batch\\.api\\.chunk\\.listener", "jakarta\\.batch\\.api\\.chunk\\.listener",
                "javax\\.batch\\.api\\.chunk", "jakarta\\.batch\\.api\\.chunk",
                "javax\\.batch\\.api\\.listener", "jakarta\\.batch\\.api\\.listener",
                "javax\\.batch\\.api\\.partition", "jakarta\\.batch\\.api\\.partition",
                "javax\\.batch\\.api", "jakarta\\.batch\\.api",
                "javax\\.batch\\.operations", "jakarta\\.batch\\.operations",
                "javax\\.batch\\.runtime\\.context", "jakarta\\.batch\\.runtime\\.context",
                "javax\\.batch\\.runtime", "jakarta\\.batch\\.runtime",
                "javax\\.decorator", "jakarta\\.decorator",
                "javax\\.ejb\\.embeddable", "jakarta\\.ejb\\.embeddable",
                "javax\\.ejb\\.spi", "jakarta\\.ejb\\.spi",
                "javax\\.ejb", "jakarta\\.ejb",
                "javax\\.el", "jakarta\\.el",
                "javax\\.enterprise\\.concurrent", "jakarta\\.enterprise\\.concurrent",
                "javax\\.enterprise\\.context\\.control", "jakarta\\.enterprise\\.context\\.control",
                "javax\\.enterprise\\.context\\.spi", "jakarta\\.enterprise\\.context\\.spi",
                "javax\\.enterprise\\.context", "jakarta\\.enterprise\\.context",
                "javax\\.enterprise\\.event", "jakarta\\.enterprise\\.event",
                "javax\\.enterprise\\.inject\\.literal", "jakarta\\.enterprise\\.inject\\.literal",
                "javax\\.enterprise\\.inject\\.se", "jakarta\\.enterprise\\.inject\\.se",
                "javax\\.enterprise\\.inject\\.spi\\.configurator", "jakarta\\.enterprise\\.inject\\.spi\\.configurator",
                "javax\\.enterprise\\.inject\\.spi", "jakarta\\.enterprise\\.inject\\.spi",
                "javax\\.enterprise\\.inject", "jakarta\\.enterprise\\.inject",
                "javax\\.enterprise\\.util", "jakarta\\.enterprise\\.util",
                "javax\\.faces\\.annotation", "jakarta\\.faces\\.annotation",
                "javax\\.faces\\.application", "jakarta\\.faces\\.application",
                "javax\\.faces\\.bean", "jakarta\\.faces\\.bean",
                "javax\\.faces\\.component\\.behavior", "jakarta\\.faces\\.component\\.behavior",
                "javax\\.faces\\.component\\.html", "jakarta\\.faces\\.component\\.html",
                "javax\\.faces\\.component\\.search", "jakarta\\.faces\\.component\\.search",
                "javax\\.faces\\.component\\.visit", "jakarta\\.faces\\.component\\.visit",
                "javax\\.faces\\.component", "jakarta\\.faces\\.component",
                "javax\\.faces\\.context", "jakarta\\.faces\\.context",
                "javax\\.faces\\.convert", "jakarta\\.faces\\.convert",
                "javax\\.faces\\.el", "jakarta\\.faces\\.el",
                "javax\\.faces\\.event", "jakarta\\.faces\\.event",
                "javax\\.faces\\.flow\\.builder", "jakarta\\.faces\\.flow\\.builder",
                "javax\\.faces\\.flow", "jakarta\\.faces\\.flow",
                "javax\\.faces\\.lifecycle", "jakarta\\.faces\\.lifecycle",
                "javax\\.faces\\.model", "jakarta\\.faces\\.model",
                "javax\\.faces\\.push", "jakarta\\.faces\\.push",
                "javax\\.faces\\.render", "jakarta\\.faces\\.render",
                "javax\\.faces\\.validator", "jakarta\\.faces\\.validator",
                "javax\\.faces\\.view\\.facelets", "jakarta\\.faces\\.view\\.facelets",
                "javax\\.faces\\.view", "jakarta\\.faces\\.view",
                "javax\\.faces\\.webapp", "jakarta\\.faces\\.webapp",
                "javax\\.faces", "jakarta\\.faces",
                "javax\\.inject", "jakarta\\.inject",
                "javax\\.interceptor", "jakarta\\.interceptor",
                "javax\\.jms", "jakarta\\.jms",
                "javax\\.json\\.bind\\.adapter", "jakarta\\.json\\.bind\\.adapter",
                "javax\\.json\\.bind\\.annotation", "jakarta\\.json\\.bind\\.annotation",
                "javax\\.json\\.bind\\.config", "jakarta\\.json\\.bind\\.config",
                "javax\\.json\\.bind\\.serializer", "jakarta\\.json\\.bind\\.serializer",
                "javax\\.json\\.bind\\.spi", "jakarta\\.json\\.bind\\.spi",
                "javax\\.json\\.bind", "jakarta\\.json\\.bind",
                "javax\\.json\\.spi", "jakarta\\.json\\.spi",
                "javax\\.json\\.stream", "jakarta\\.json\\.stream",
                "javax\\.json", "jakarta\\.json",
                "javax\\.jws\\.soap", "jakarta\\.jws\\.soap",
                "javax\\.jws", "jakarta\\.jws",
                "javax\\.mail\\.event", "jakarta\\.mail\\.event",
                "javax\\.mail\\.internet", "jakarta\\.mail\\.internet",
                "javax\\.mail\\.search", "jakarta\\.mail\\.search",
                "javax\\.mail\\.util", "jakarta\\.mail\\.util",
                "javax\\.mail", "jakarta\\.mail",
                "javax\\.persistence\\.criteria", "jakarta\\.persistence\\.criteria",
                "javax\\.persistence\\.metamodel", "jakarta\\.persistence\\.metamodel",
                "javax\\.persistence\\.spi", "jakarta\\.persistence\\.spi",
                "javax\\.persistence", "jakarta\\.persistence",
                "javax\\.resource\\.cci", "jakarta\\.resource\\.cci",
                "javax\\.resource\\.spi\\.endpoint", "jakarta\\.resource\\.spi\\.endpoint",
                "javax\\.resource\\.spi\\.security", "jakarta\\.resource\\.spi\\.security",
                "javax\\.resource\\.spi\\.work", "jakarta\\.resource\\.spi\\.work",
                "javax\\.resource\\.spi", "jakarta\\.resource\\.spi",
                "javax\\.resource", "jakarta\\.resource",
                "javax\\.security\\.auth\\.message\\.callback", "jakarta\\.security\\.auth\\.message\\.callback",
                "javax\\.security\\.auth\\.message\\.config", "jakarta\\.security\\.auth\\.message\\.config",
                "javax\\.security\\.auth\\.message\\.module", "jakarta\\.security\\.auth\\.message\\.module",
                "javax\\.security\\.auth\\.message", "jakarta\\.security\\.auth\\.message",
                "javax\\.security\\.enterprise\\.authentication\\.mechanism\\.http", "jakarta\\.security\\.enterprise\\.authentication\\.mechanism\\.http",
                "javax\\.security\\.enterprise\\.credential", "jakarta\\.security\\.enterprise\\.credential",
                "javax\\.security\\.enterprise\\.identitystore", "jakarta\\.security\\.enterprise\\.identitystore",
                "javax\\.security\\.enterprise", "jakarta\\.security\\.enterprise",
                "javax\\.security\\.jacc", "jakarta\\.security\\.jacc",
                "javax\\.servlet\\.annotation", "jakarta\\.servlet\\.annotation",
                "javax\\.servlet\\.descriptor", "jakarta\\.servlet\\.descriptor",
                "javax\\.servlet\\.http", "jakarta\\.servlet\\.http",
                "javax\\.servlet\\.jsp\\.el", "jakarta\\.servlet\\.jsp\\.el",
                "javax\\.servlet\\.jsp\\.jstl\\.core", "jakarta\\.servlet\\.jsp\\.jstl\\.core",
                "javax\\.servlet\\.jsp\\.jstl\\.fmt", "jakarta\\.servlet\\.jsp\\.jstl\\.fmt",
                "javax\\.servlet\\.jsp\\.jstl\\.sql", "jakarta\\.servlet\\.jsp\\.jstl\\.sql",
                "javax\\.servlet\\.jsp\\.jstl\\.tlv", "jakarta\\.servlet\\.jsp\\.jstl\\.tlv",
                "javax\\.servlet\\.jsp\\.jstl", "jakarta\\.servlet\\.jsp\\.jstl",
                "javax\\.servlet\\.jsp\\.resources", "jakarta\\.servlet\\.jsp\\.resources",
                "javax\\.servlet\\.jsp\\.tagext", "jakarta\\.servlet\\.jsp\\.tagext",
                "javax\\.servlet\\.jsp", "jakarta\\.servlet\\.jsp",
                "javax\\.servlet\\.resources", "jakarta\\.servlet\\.resources",
                "javax\\.servlet", "jakarta\\.servlet",
                "javax\\.transaction", "jakarta\\.transaction",
                "javax\\.validation\\.bootstrap", "jakarta\\.validation\\.bootstrap",
                "javax\\.validation\\.constraints", "jakarta\\.validation\\.constraints",
                "javax\\.validation\\.constraintvalidation", "jakarta\\.validation\\.constraintvalidation",
                "javax\\.validation\\.executable", "jakarta\\.validation\\.executable",
                "javax\\.validation\\.groups", "jakarta\\.validation\\.groups",
                "javax\\.validation\\.metadata", "jakarta\\.validation\\.metadata",
                "javax\\.validation\\.spi", "jakarta\\.validation\\.spi",
                "javax\\.validation\\.valueextraction", "jakarta\\.validation\\.valueextraction",
                "javax\\.validation", "jakarta\\.validation",
                "javax\\.websocket\\.server", "jakarta\\.websocket\\.server",
                "javax\\.websocket", "jakarta\\.websocket",
                "javax\\.ws\\.rs\\.client", "jakarta\\.ws\\.rs\\.client",
                "javax\\.ws\\.rs\\.container", "jakarta\\.ws\\.rs\\.container",
                "javax\\.ws\\.rs\\.core", "jakarta\\.ws\\.rs\\.core",
                "javax\\.ws\\.rs\\.ext", "jakarta\\.ws\\.rs\\.ext",
                "javax\\.ws\\.rs\\.sse", "jakarta\\.ws\\.rs\\.sse",
                "javax\\.ws\\.rs", "jakarta\\.ws\\.rs",
                "javax\\.xml\\.bind\\.annotation\\.adapters", "jakarta\\.xml\\.bind\\.annotation\\.adapters",
                "javax\\.xml\\.bind\\.annotation", "jakarta\\.xml\\.bind\\.annotation",
                "javax\\.xml\\.bind\\.attachment", "jakarta\\.xml\\.bind\\.attachment",
                "javax\\.xml\\.bind\\.helpers", "jakarta\\.xml\\.bind\\.helpers",
                "javax\\.xml\\.bind\\.util", "jakarta\\.xml\\.bind\\.util",
                "javax\\.xml\\.bind", "jakarta\\.xml\\.bind",
                "javax\\.xml\\.soap", "jakarta\\.xml\\.soap",
                "javax\\.xml\\.ws\\.handler\\.soap", "jakarta\\.xml\\.ws\\.handler\\.soap",
                "javax\\.xml\\.ws\\.handler", "jakarta\\.xml\\.ws\\.handler",
                "javax\\.xml\\.ws\\.http", "jakarta\\.xml\\.ws\\.http",
                "javax\\.xml\\.ws\\.soap", "jakarta\\.xml\\.ws\\.soap",
                "javax\\.xml\\.ws\\.spi\\.http", "jakarta\\.xml\\.ws\\.spi\\.http",
                "javax\\.xml\\.ws\\.spi", "jakarta\\.xml\\.ws\\.spi",
                "javax\\.xml\\.ws\\.wsaddressing", "jakarta\\.xml\\.ws\\.wsaddressing",
                "javax\\.xml\\.ws", "jakarta\\.xml\\.ws"
            ));
        }

        // Switch POMs to jakarta specs and other upgrades
        System.out.println("Switch POMs to jakarta specs and other upgrades");
        try (Stream<Path> files = Files.walk(Paths.get("."))) {
            files
                .filter(p -> p.getFileName().toString().equals("pom.xml"))
                .forEach(p -> regex(p, 
                    // Mail
                    "<(jakarta-mail-version)>.*?</\\1>", "<$1>2.0.1</$1>",
                    "<(mock-javamail-version)>.*?</\\1>", "<$1>2.0</$1>",
                    // JAXB
                    "jaxb-api-version", "jakarta-xml-bind-api-version",
                    "jakarta-jaxb-version", "jakarta-xml-bind-api-version",
                    "(<dependency>\\s*)<groupId>javax\\.xml\\.bind</groupId>(\\s*)<artifactId>jaxb-api</artifactId>", "$1<groupId>jakarta.xml.bind</groupId>$2<artifactId>jakarta.xml.bind-api</artifactId>",
                    "(<dependency>\\s*<groupId>jakarta\\.xml\\.bind</groupId>\\s*<artifactId>jakarta\\.xml\\.bind-api</artifactId>\\s*)<version>.*?</version>", "$1<version>\\$\\{jakarta-xml-bind-api-version\\}</version>",
                    "<(jakarta-xml-bind-api-version)>.*?</\\1>", "<$1>4.0.0</$1>",
                    "<(glassfish-jaxb-runtime-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    "<(jaxb2-maven-plugin-version)>.*?</\\1>", "<$1>3.1.0</$1>",
                    "<(jaxb-core-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    "<(jaxb-impl-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    "<(jaxb-osgi-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    // Activation
                    "javax-activation-version", "jakarta-activation-version",
                    "<(jakarta-activation-version)>.*?</\\1>", "<$1>2.0.1</$1>",
                    "(<dependency>\\s*<groupId>com\\.sun\\.activation</groupId>\\s*)<artifactId>javax\\.activation</artifactId>", "$1<artifactId>jakarta.activation</artifactId>",
                    "(<dependency>\\s*(<\\!--.*?-->\\s*)?)<groupId>javax\\.activation</groupId>(\\s*)<artifactId>javax\\.activation-api</artifactId>", "$1<groupId>jakarta.activation</groupId>$3<artifactId>jakarta.activation-api</artifactId>",
                    // Annotations
                    "javax-annotation-api-version", "jakarta-annotation-api-version",
                    "<(jakarta-annotation-api-version)>.*?</\\1>", "<$1>2.1.1</$1>",
                    "(<dependency>\\s*)<groupId>javax\\.annotation</groupId>(\\s*)<artifactId>javax\\.annotation-api</artifactId>", "$1<groupId>jakarta.annotation</groupId>$2<artifactId>jakarta.annotation-api</artifactId>",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-annotation_1.0_spec</artifactId>", "$1<groupId>jakarta.annotation</groupId>$2<artifactId>jakarta.annotation-api</artifactId>",
                    "(<dependency>\\s*<groupId>jakarta\\.annotation</groupId>\\s*<artifactId>jakarta\\.annotation-api</artifactId>\\s*)<version>.*?</version>", "$1<version>\\$\\{jakarta-annotation-api-version\\}</version>",
                    // Spring
                    " *<(spring5-version)>.*?</\\1> *\n", "",
                    "spring5-version", "spring-version",
                    "<(spring-version)>.*?</\\1>", "<$1>6.0.2</$1>",
                    "<(spring-ws-version)>.*?</\\1>", "<$1>4.0.0</$1>",
                    // CDI
                    "weld3-version", "weld-version",
                    "<(weld-version)>.*?</\\1>", "<$1>5.1.0.Final</$1>",
                    "cdi-api-2.0-version", "jakarta-enterprise-cdi-api-version",
                    "jakarta-cdi-api-version", "jakarta-enterprise-cdi-api-version",
                    "<(jakarta-enterprise-cdi-api-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    "(<dependency>\\s*)<groupId>javax\\.enterprise</groupId>(\\s+)<artifactId>cdi-api</artifactId>", "$1<groupId>jakarta.enterprise</groupId>$2<artifactId>jakarta.enterprise.cdi-api</artifactId>",
                    // JMS
                    "geronimo-jms-spec-version", "jakarta-jms-api-version",
                    "<(qpid-jms-client-version)>.*?</\\1>", "<$1>2.1.0</$1>",
                    "<(jakarta-jms-api-version)>.*?</\\1>", "<$1>3.1.0</$1>",
                    " *<(geronimo-jms2-spec-version)>.*?</\\1> *\n", "",
                    "geronimo-jms2-spec-version", "jakarta-jms-api-version",
                    "<artifactId>artemis-jms-", "<artifactId>artemis-jakarta-",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-jms_1.1_spec</artifactId>", "$1<groupId>jakarta.jms</groupId>$2<artifactId>jakarta.jms-api</artifactId>",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-jms_2.0_spec</artifactId>", "$1<groupId>jakarta.jms</groupId>$2<artifactId>jakarta.jms-api</artifactId>",
                    "(<dependency>\\s*<groupId>jakarta.jms</groupId>\\s*<artifactId>jakarta.jms-api</artifactId>\\s*<version>[^<]*</version>\\s*</dependency>\\s*)<dependency>\\s*<groupId>jakarta.jms</groupId>\\s*<artifactId>jakarta.jms-api</artifactId>\\s*<version>[^<]*</version>\\s*</dependency>", "$1",
                    // JPA
                    "geronimo-jpa-spec-version", "jakarta-persistence-api-version",
                    "<(jakarta-persistence-api-version)>.*?</\\1>", "<$1>3.1.0</$1>",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-jpa_2.1_spec</artifactId>", "$1<groupId>jakarta.persistence</groupId>$2<artifactId>jakarta.persistence-api</artifactId>",
                    // JTA
                    "geronimo-jta-spec-version", "jakarta-transaction-api-version",
                    "<(jakarta-transaction-api-version)>.*?</\\1>", "<$1>2.0.1</$1>",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-jta_1.1_spec</artifactId>", "$1<groupId>jakarta.transaction</groupId>$2<artifactId>jakarta.transaction-api</artifactId>",
                    // Servlet
                    "<(undertow-version)>.*?</\\1>", "<$1>2.3.0.Final</$1>",
                    " *<(jetty9-version)>.*?</\\1> *\n", "",
                    "jetty9-version", "jetty-version",
                    "<(jetty-version)>.*?</\\1>", "<$1>11.0.12</$1>",
                    "javax-servlet-api-version", "jakarta-servlet-api-version",
                    "<(jakarta-servlet-api-version)>.*?</\\1>", "<$1>6.0.0</$1>",
                    "(<groupId>org\\.eclipse\\.jetty\\.websocket</groupId>\\s*<artifactId>websocket)-(client|server|api)(</artifactId>)", "$1-jakarta-$2$3",
                    "(<dependency>\\s*)<groupId>javax\\.servlet</groupId>(\\s*)<artifactId>javax\\.servlet-api</artifactId>", "$1<groupId>jakarta.servlet</groupId>$2<artifactId>jakarta.servlet-api</artifactId>",
                    "<(cxf-version)>.*?</\\1>", "<$1>4.0.0-SNAPSHOT</$1>",
                    "<(cxf-codegen-plugin-version)>.*?</\\1>", "<$1>4.0.0-SNAPSHOT</$1>",
                    "<(cxf-xjc-plugin-version)>.*?</\\1>", "<$1>4.0.0</$1>",
                    "<(cxf-xjc-utils-version)>.*?</\\1>", "<$1>4.0.0</$1>",
                    "<frontEnd>jaxws21</frontEnd>", "<frontEnd>jaxws</frontEnd>",
                    "Camel Jetty 9\\.x support", "Camel Jetty 11.x support",
                    "<atmosphere-version>2.6.4</atmosphere-version>", "<atmosphere-version>3.0.2</atmosphere-version>",
                    "<(cometd-java-server)>.*?</\\1>", "<$1>7.0.9</$1>",
                    "<(cometd-java-client-version)>.*?</\\1>", "<$1>7.0.9</$1>",
                    "<artifactId>cometd-java-server</artifactId>", "<artifactId>cometd-java-server-websocket-jetty</artifactId>",
                    "<artifactId>cometd-java-client</artifactId>", "<artifactId>cometd-java-client-websocket-jetty</artifactId>",
                    "cometd-java-server(?!-websocket-jetty)", "cometd-java-server-version",
                    " *<dependency>\\s*<groupId>org.cometd.java</groupId>\\s*<artifactId>bayeux-api</artifactId>\\s*<version>[^<]+</version>\\s*</dependency> *\n", "",
                    // JAXWS
                    "<(jaxb-jxc-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    "<(jaxb-xjc-version)>.*?</\\1>", "<$1>4.0.1</$1>",
                    "<(javax-soap-api-version)>.*?</\\1>", "<$1>3.0.0</$1>",
                    "geronimo-ws-metadata-spec-version", "jakarta-jws-api-version",
                    "jaxws-api-version", "jakarta-xml-ws-api-version",
                    "javax-soap-api-version", "jakarta-xml-soap-api-version",
                    "<(jakarta-xml-soap-api-version)>.*?</\\1>", "<$1>3.0.0</$1>",
                    "<(jakarta-xml-ws-api-version)>.*?</\\1>", "<$1>4.0.0</$1>",
                    "<(jakarta-jws-api-version)>.*?</\\1>", "<$1>3.0.0</$1>",
                    "(<dependency>\\s*)<groupId>javax\\.xml\\.ws</groupId>(\\s*)<artifactId>jaxws-api</artifactId>", "$1<groupId>jakarta.xml.ws</groupId>$2<artifactId>jakarta.xml.ws-api</artifactId>",
                    "(<dependency>\\s*)<groupId>org\\.apache\\.geronimo\\.specs</groupId>(\\s*)<artifactId>geronimo-ws-metadata_2.0_spec</artifactId>", "$1<groupId>jakarta.jws</groupId>$2<artifactId>jakarta.jws-api</artifactId>",
                    "(<dependency>\\s*)<groupId>javax\\.xml\\.soap</groupId>(\\s*)<artifactId>javax\\.xml\\.soap-api</artifactId>", "$1<groupId>jakarta.xml.soap</groupId>$2<artifactId>jakarta.xml.soap-api</artifactId>",
                    // JSON
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-json_1.1_spec</artifactId>", "$1<groupId>jakarta.json</groupId>$2<artifactId>jakarta.json-api</artifactId>",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-jsonb_1.0_spec</artifactId>", "$1<groupId>jakarta.json.bind</groupId>$2<artifactId>jakarta.json.bind-api</artifactId>",
                    "(<dependency>\\s*)<groupId>org.apache.geronimo.specs</groupId>(\\s*)<artifactId>geronimo-jms_1.1_spec</artifactId>", "$1<groupId>jakarta.jms</groupId>$2<artifactId>jakarta.jws-api</artifactId>",
                    "(<dependency>\\s*<groupId>jakarta\\.json</groupId>\\s*<artifactId>jakarta\\.json-api</artifactId>\\s*)<version>.*?</version>", "$1<version>\\$\\{jakarta-json-api-version\\}</version>",
                    "(<dependency>\\s*<groupId>jakarta\\.json\\.bind</groupId>\\s*<artifactId>jakarta\\.json\\.bind-api</artifactId>\\s*)<version>.*?</version>", "$1<version>\\$\\{jakarta-json-bind-api-version\\}</version>",
                    "geronimo-json-spec-version", "jakarta-json-api-version",
                    "geronimo-jsonb-spec-version", "jakarta-json-bind-api-version",
                    "<(jakarta-json-api-version)>.*?</\\1>", "<$1>2.1.1</$1>",
                    "<(jakarta-json-bind-api-version)>.*?</\\1>", "<$1>3.0.0</$1>",
                    // Validator
                    "<(hibernate-validator-version)>.*?</\\1>", "<$1>8.0.0.Final</$1>",
                    // Citrus
                    "<(citrus-version)>.*?</\\1>", "<$1>3.3.0</$1>",
                    // RestEasy
                    "<(resteasy-version)>.*?</\\1>", "<$1>6.2.1.Final</$1>",
                    " *<dependency>\\s*<groupId>org.jboss.resteasy</groupId>\\s*<artifactId>resteasy-spring</artifactId>\\s*<scope>test</scope>\\s*</dependency>", "",
                    "<groupId>com.fasterxml.jackson.jaxrs</groupId>(\\s*)<artifactId>jackson-jaxrs-json-provider</artifactId>", "<groupId>com.fasterxml.jackson.jakarta.rs</groupId>$1<artifactId>jackson-jakarta-rs-json-provider</artifactId>"
                ));
        }

        regex(Paths.get("pom.xml"), "(( *)<jaxb-xjc-version>.*</jaxb-xjc-version>)", "$1\n$2<jaxb-jxc-version>4.0.1</jaxb-jxc-version>");
        regex(Paths.get("parent/pom.xml"), 
                "(<plugin>\\s*<groupId>org.codehaus.mojo</groupId>\\s*<artifactId>jaxb2-maven-plugin</artifactId>[\\s\\S]*?)(</dependencies>\\s*</plugin>)", 
                "$1    <dependency>\n            <groupId>com.sun.xml.bind</groupId>\n             <artifactId>jaxb-xjc</artifactId>\n            <version>\\${jaxb-xjc-version}</version>\n        </dependency>\n        <dependency>\n            <groupId>com.sun.xml.bind</groupId>\n            <artifactId>jaxb-jxc</artifactId>\n            <version>\\${jaxb-jxc-version}</version>\n        </dependency>\n    $2");

        // Spring
        regex(Paths.get("components/camel-test/pom.xml"), "<module>camel-test-spring</module>", "<!--module>camel-test-spring</module-->");
        regex(Paths.get("components/camel-spring-xml/src/test/java/org/apache/camel/spring/postprocessor/MagicAnnotationPostProcessor.java"),
                "InstantiationAwareBeanPostProcessorAdapter", "SmartInstantiationAwareBeanPostProcessor",
                "extends", "implements");

        // CDI
        regex(Paths.get("components/camel-test/pom.xml"), "<module>camel-test-cdi</module>", "<!--module>camel-test-cdi</module-->");
        try (Stream<Path> files = Files.walk(Paths.get("components/camel-cdi/src"))) {
            files
            .filter(p -> p.getFileName().toString().endsWith(".java"))
            .forEach(p -> regex(p, 
                "manager\\.createInjectionTarget\\((.*?)\\)\\;", "manager\\.getInjectionTargetFactory(\n                $1).createInjectionTarget(null)\\;",
                " @Override\\s+public boolean isNullable\\(\\)[\\s\n]+\\{\\s+return [^;]+;\\s+} *\n *\n", "",
                "manager\\.fireEvent\\(([a-z]+), (.*?)\\);", "manager.getEvent().select($2).fire($1);",
                "manager\\.fireEvent\\(([a-z]+)\\);", "manager.getEvent().fire($1);"
            ));
        }

        // JAXB
        regex(Paths.get("components/camel-jaxb/src/main/java/org/apache/camel/converter/jaxb/mapper/DefaultNamespacePrefixMapper.java"),
            "com.sun.xml.bind.marshaller", "org.glassfish.jaxb.runtime.marshaller");
        regex(Paths.get("components/camel-jmx/src/main/resources/notifications.xsd"),
            "jxb:version=\"1\\.0\"", "jxb:version=\"3\\.0\"");

        // JMS
        regex(Paths.get("parent/pom.xml"), 
                " *<dependency>\\s+<groupId>jakarta.jms</groupId>\\s*<artifactId>jakarta.jms-api</artifactId>\\s+<version>\\$\\{geronimo-jms2-spec-version\\}</version>\\s+</dependency> *\n",
                "");
        regex(Paths.get("components/pom.xml"), 
            "<module>camel-activemq</module>", "<!--module>camel-activemq</module-->",
                "<module>camel-stomp</module>", "<!--module>camel-stomp</module-->");
        regex(Paths.get("test-infra/pom.xml"), 
            "<module>camel-test-infra-activemq</module>", "<!--module>camel-test-infra-activemq</module-->");
        Stream.of("components/camel-jms/pom.xml", "components/camel-sjms/pom.xml",
                        "components/camel-mllp/pom.xml", "components/camel-sjms2/pom.xml")
            .map(Paths::get)
            .forEach(p -> regex(p, 
                "<artifactId>camel-test-infra-activemq</artifactId>", "<artifactId>camel-test-infra-artemis</artifactId>",
                " *<dependency>\\s*<groupId>org.apache.activemq</groupId>\\s*<artifactId>activemq-[\\s\\S]*?</dependency> *", "",
                "<build>\n( *)<plugins>", "<build>\n$1<testSourceDirectory>src/test/dummy</testSourceDirectory>\n$1<plugins>"));
        Stream.of("components/camel-amqp/pom.xml", "components/camel-paho/pom.xml")
            .map(Paths::get)
            .forEach(p -> regex(p, 
                "<artifactId>camel-test-infra-activemq</artifactId>", "<artifactId>camel-test-infra-artemis</artifactId>",
                " *<dependency>\\s*<groupId>org.apache.activemq</groupId>\\s*<artifactId>activemq-[\\s\\S]*?</dependency> *", "",
                "(( *)</dependencies> *\n)", "$1\n$2<build>\n$2    <testSourceDirectory>src/test/dummy</testSourceDirectory>\n$2</build>\n"));


        // JAXWS
        try (Stream<Path> files = Files.walk(Paths.get("components"))) {
            files
            .filter(p -> {
                var name = p.getFileName().toString();
                return name.endsWith(".xml") || name.endsWith(".xsd") || name.endsWith(".wsdl");
            })
            .forEach(p -> regex(p,
                "(<web-app\s([^>]*\s)?)version=\"3\\.[0-9]\"", "$1version=\"6.0\"", 
                "(<jaxb:bindings[^>]*\s(jaxb:)?)version=\"2\\.[0-9]\"", "$1version=\"3.0\"",
                "http://java.sun.com/xml/ns/jaxb/bindingschema_2_0.xsd", "https://jakarta.ee/xml/ns/jaxb/bindingschema_3_0.xsd",
                "http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd", "https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd",
                "http://java.sun.com/xml/ns/jaxb(?!/xjc)", "https://jakarta.ee/xml/ns/jaxb",
                "http://java.sun.com/xml/ns/jaxws", "https://jakarta.ee/xml/ns/jaxws",
                "http://java.sun.com/xml/ns/javaee", "https://jakarta.ee/xml/ns/jakartaee"
            ));
        }

        // Cometd
        regex(Paths.get("components/camel-cometd/src/main/java/org/apache/camel/component/cometd/CometdProducer.java"), 
            "channel\\.publish\\(serverSession, mutable\\)\\;", "channel.publish(serverSession, mutable, new org.cometd.bayeux.Promise<>() {});");
        for (String path : List.of("Authenticated", "Extension")) {
            regex(Paths.get("components/camel-cometd/src/test/java/org/apache/camel/component/cometd/CometdProducerConsumerInteractive" + path + "ManualTest.java"), 
                "ServerSession\\.RemoveListener", "ServerSession.RemovedListener",
                "removed\\(ServerSession session, boolean timeout\\)", "removed(ServerSession session, ServerMessage message, boolean timeout)");
        }

        // Consul + Google Sheets
        Stream.of("ConsulServiceCallWithRegistrationIT", "ConsulServiceRegistrationTestBase", "ConsulServiceDiscoveryIT")
            .map(s -> "components/camel-consul/src/test/java/org/apache/camel/component/consul/cloud/" + s + ".java")
            .map(Paths::get)
            .forEach(p -> regex(p, 
                "import org\\.springframework\\.util\\.SocketUtils;", "import org.apache.camel.test.AvailablePortFinder;",
                "SocketUtils\\.findAvailableTcpPort\\(\\)", "AvailablePortFinder.getNextAvailable()"));

        // Disable tests on google-sheets
        // regex(Paths.get("components/camel-google/camel-google-sheets/pom.xml"),
            // "<build>\n( *)<defaultGoal>", "<build>\n$1<testSourceDirectory>src/test/dummy</testSourceDirectory>\n$1<defaultGoal>",
            // "(<execution>[\\s\\S]*?</execution>)", "<!-- $1 -->");

        regex(Paths.get("components/camel-jclouds/src/main/java/org/apache/camel/component/jclouds/JcloudsBlobStoreHelper.java"),
            "MediaType\\.APPLICATION_OCTET_STREAM", "APPLICATION_OCTET_STREAM",
            "import jakarta\\.ws\\.rs\\.core\\.MediaType;\n*", "",
            "(( *)private JcloudsBlobStoreHelper)", "$2private static final String APPLICATION_OCTET_STREAM = \"application/octet-stream\";\n$1");

        // Johnzon
        regex(Paths.get("components/pom.xml"),
                "<module>camel-johnzon</module>", "<!--module>camel-johnzon</module-->");

        // Add missing dependency
        regex(Paths.get("components/camel-cxf/camel-cxf-common/pom.xml"), 
                "(<dependency>\n(\\s*)<groupId>[^<]*</groupId>\n\\s*<artifactId>[^<]*</artifactId>\n\\s*<scope>test</scope>\n(\\s*)</dependency>)",
                "<dependency>\n$2<groupId>jakarta.xml.soap</groupId>\n$2<artifactId>jakarta.xml.soap-api</artifactId>\n$3</dependency>\n\n$3$1");

        // websockets
        regex(Paths.get("components/pom.xml"),
                "<module>camel-websocket</module>", "<!--module>camel-websocket</module-->");
        regex(Paths.get("components/camel-websocket-jsr356/pom.xml"),
                "<(tomcat\\.version)>.*?</\\1>", "<$1>10.0.27</$1>",
                "(( *)<dependency>\\s*<groupId>org.apache.tomcat</groupId>\\s*<artifactId>tomcat-websocket</artifactId>\\s*<version>\\$\\{tomcat.version\\}</version>\\s*<scope>test</scope>\\s*</dependency> *\n)",
                "$1$2<dependency>\n" +
                        "            <groupId>jakarta.enterprise</groupId>\n" +
                        "            <artifactId>jakarta.enterprise.cdi-api</artifactId>\n" +
                        "            <version>\\$\\{jakarta-enterprise-cdi-api-version\\}</version>\n" +
                        "            <scope>test</scope>\n" +
                        "        </dependency>\n");

        // Add repo
        regex(Paths.get("components/camel-mail/pom.xml"),
            "</properties>", "</properties>\n\n            <repositories>\n                <repository>\n                    <id>central</id>\n                    <url>https://repo1.maven.org/maven2/</url>\n                </repository>\n                <repository>\n                    <id>jenkinsci-releases</id>\n                    <url>https://repo.jenkins-ci.org/releases/</url>\n                </repository>\n            </repositories>");

        // Jackson JAXRS
        Stream.of(
                "components/camel-servicenow/camel-servicenow-component/src/main/java/org/apache/camel/component/servicenow/ServiceNowClient.java",
                "components/camel-wordpress/src/main/java/org/apache/camel/component/wordpress/api/service/impl/AbstractWordpressServiceAdapter.java",
                "components/camel-bonita/src/main/java/org/apache/camel/component/bonita/api/util/BonitaAPIUtil.java",
                "components/camel-bonita/src/main/java/org/apache/camel/component/bonita/api/BonitaAPIBuilder.java")
            .map(Paths::get)
            .forEach(p -> regex(p, 
                "com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider", "com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider"));

        Stream.of("catalog/camel-allcomponents/pom.xml",
                  "catalog/camel-catalog/pom.xml",
                  "bom/camel-bom/pom.xml",
                  "parent/pom.xml",
                  "tests/camel-itest/pom.xml",
                  "catalog/camel-route-parser/pom.xml")
            .map(Paths::get)
            .forEach(p -> regex(p, 
                  " *<dependency>\\s*<groupId>org.apache.camel</groupId>\\s*<artifactId>camel-(activemq|johnzon|stomp|test-cdi|test-spring|websocket)</artifactId>[\\s\\S]*?</dependency> *\n+", ""));

        // camel-jpa
        regex(Paths.get("components/camel-jpa/pom.xml"),
            "(<dependency>\\s*<groupId>jakarta.persistence</groupId>\\s*<artifactId>jakarta.persistence-api</artifactId>)\\s*<scope>provided</scope>(\\s*</dependency>)",
            "$1$2");
        // camel-cdi
        regex(Paths.get("components/camel-cdi/pom.xml"),
            "(<dependency>\\s*<groupId>jakarta.enterprise</groupId>\\s*<artifactId>jakarta.enterprise.cdi-api</artifactId>\\s*<version>[^<]*</version>)\\s*<scope>provided</scope>(\\s*</dependency>)",
            "$1$2");

        // camel-itest
        regex(Paths.get("tests/camel-itest/pom.xml"),
            "( *<\\!--.*?--> *\n)? *<dependency>\\s*<groupId>jakarta.transaction</groupId>\\s*<artifactId>jakarta.transaction-api</artifactId>[\\s\\S]*?</dependency> *\n", "",
            " *<dependency>\\s*<groupId>org.apache.activemq</groupId>[\\s\\S]*?</dependency> *\n", "");
        regex(Paths.get("tests/camel-itest/src/test/java/org/apache/camel/itest/CamelJmsTestHelper.java"),
            "import org\\.apache\\.activemq\\.[^;]+; *\n", "",
            "(create(Persistent)?ConnectionFactory\\(String options\\) \\{\\s*)[\\s\\S]*?return pooled;", "$1throw new UnsupportedOperationException();");

        // Commit
        exec("git", "commit", "-a", "-m", "Switch to jakarta namespace");

        // Jetty 9 -> 11 migration
        exec("git", "cherry-pick", "-x", "5e57bb6cc86bfdbc474f91fc2c152cb0fc03a9ff");
        // Salesforce migration
        exec("git", "cherry-pick", "-x", "eda15c498fb3834996924607e353ecb1e37cffcc");

        // rm -Rf **\/src/generated target **\/target && mvn source:jar install -DskipTests 

        return 0;
    }

    private static void regex(Path path, String... expressions) throws IOError {
        try {
            var org = Files.readString(path);
            var content = org;
            for (int i = 0; i < expressions.length; i += 2) {
                content = content.replaceAll(expressions[i], expressions[i+1]);
            }
            if (!Objects.equals(org, content)) {
                Files.writeString(path, content, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (MalformedInputException e) {
            System.out.println("Ignoring file because of bad encoding: " + path);
        } catch (IOException e) {
            throw new IOError(new IOException("Error processing file: " + path, e));
        }
    }

    private static void exec(String... cmd) throws IOException {
        var pb = new ProcessBuilder(cmd);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw (InterruptedIOException) new InterruptedIOException("Command interrupted").initCause(e);
        }
        if (p.exitValue() != 0) {
            throw new IOException("Error executing '" + String.join(" ", (CharSequence[]) cmd));
        }
    }
}
