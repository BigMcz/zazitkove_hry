package cz.mibcon.zazitky.cardprint;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.AbstractResource;

/** A generated file sent once: it is deleted when the response has read it. */
class TempFileResource extends AbstractResource {

    private final Path path;

    TempFileResource(Path path) {
        this.path = path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FilterInputStream(Files.newInputStream(path)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    CardPrintService.deleteQuietly(path);
                }
            }
        };
    }

    @Override
    public long contentLength() throws IOException {
        return Files.size(path);
    }

    @Override
    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public String getDescription() {
        return "generated print file";
    }
}
