package org.apache.camel.component.firebase.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copies a test file to a folder to test routes.
 */
public class SampleInputProvider {

    private final Path targetFolder;

    public SampleInputProvider() throws IOException {
        targetFolder = Paths.get("src/data");
        if(!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }
    }

    public void copySampleFile() throws URISyntaxException, IOException {
        final String name = "sample_message.txt";
        URL url = Thread.currentThread().getContextClassLoader().getResource(name);
        assertThat(url).isNotNull();
        final URI uri = url.toURI();
        Path sourceFile = Paths.get(uri);
        Files.copy(sourceFile, targetFolder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    public Path getTargetFolder() {
        return targetFolder;
    }

    public static String createDeleteKey() {
        return "second king of Saxony";
    }
}
