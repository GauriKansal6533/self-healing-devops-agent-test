
package com.gauri.self_healing_devops_agent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class SourceFileReaderService {

    /**
     * Searches src/main/java recursively for the requested Java file
     * and returns its complete source code.
     */
    public void writeSourceFile(Path filePath, String newContent) throws IOException {
        Files.writeString(filePath, newContent);
    }
    public String findAndReadSourceFile(
            Path repoRoot,
            String fileNameHint
    ) throws IOException {

        Path mainSrc = repoRoot.resolve("src/main/java");

        if (!Files.exists(mainSrc)) {
            throw new IOException(
                    "No src/main/java directory found in repository: "
                            + repoRoot
            );
        }

        try (Stream<Path> paths = Files.walk(mainSrc)) {

            Path match = paths
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .equals(fileNameHint)
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new IOException(
                                    "Source file not found: "
                                            + fileNameHint
                            )
                    );

            return Files.readString(match);
        }
    }

    /**
     * Searches src/main/java recursively and returns the
     * complete path of the requested Java file.
     */
    public Path findSourceFilePath(
            Path repoRoot,
            String fileNameHint
    ) throws IOException {

        Path mainSrc = repoRoot.resolve("src/main/java");

        if (!Files.exists(mainSrc)) {
            throw new IOException(
                    "No src/main/java directory found in repository: "
                            + repoRoot
            );
        }

        try (Stream<Path> paths = Files.walk(mainSrc)) {

            return paths
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .equals(fileNameHint)
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new IOException(
                                    "Source file not found: "
                                            + fileNameHint
                            )
                    );
        }
    }
}

