package io.forestall.reactiveprocess;

import java.io.InputStream;

public class ProcessOutput<S extends InputStream, T> {

    private final S stdout;
    private final S stderr;

    private final T tag;

    public ProcessOutput(S stdout, S stderr, T tag) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.tag = tag;
    }

    public S getStdout() {
        return stdout;
    }

    public T getTag() {
        return tag;
    }

    public S getStderr() {
        return stderr;
    }
}
