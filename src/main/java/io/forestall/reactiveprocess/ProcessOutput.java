package io.forestall.reactiveprocess;

import java.io.BufferedInputStream;

public class ProcessOutput<T> {

    private final BufferedInputStream stdout;
    private final BufferedInputStream stderr;

    private final T tag;

    public ProcessOutput(BufferedInputStream stdout, BufferedInputStream stderr, T tag) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.tag = tag;
    }

    public BufferedInputStream getStdout() {
        return stdout;
    }

    public T getTag() {
        return tag;
    }

    public BufferedInputStream getStderr() {
        return stderr;
    }
}
