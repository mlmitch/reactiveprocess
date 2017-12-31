package io.forestall.reactiveprocess;

import java.io.InputStream;

public class ProcessInput<S extends InputStream, T> {

    private final S stdin;

    private final T tag;

    public ProcessInput(S stdin, T tag) {
        this.stdin = stdin;
        this.tag = tag;
    }

    public S getStdin() {
        return stdin;
    }

    public T getTag() {
        return tag;
    }
}
