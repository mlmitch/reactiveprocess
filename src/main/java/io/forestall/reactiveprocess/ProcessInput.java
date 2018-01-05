package io.forestall.reactiveprocess;

import java.io.InputStream;

public class ProcessInput<T> {

    private final InputStream stdin;

    private final T tag;

    public ProcessInput(InputStream stdin, T tag) {
        this.stdin = stdin;
        this.tag = tag;
    }

    public InputStream getStdin() {
        return stdin;
    }

    public T getTag() {
        return tag;
    }
}
