package io.forestall.reactiveprocess;

import java.io.BufferedInputStream;

public class ProcessOutput<T> {

    //using the handle prevents access to stdin
    private final ProcessHandle processHandle;

    private final BufferedInputStream stdout;
    private final BufferedInputStream stderr;

    private final T tag;


    public ProcessOutput(ProcessHandle processHandle, BufferedInputStream stdout, BufferedInputStream stderr, T tag) {
        this.processHandle = processHandle;
        this.stdout = stdout;
        this.stderr = stderr;
        this.tag = tag;
    }

    public ProcessHandle getProcessHandle() {
        return processHandle;
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
