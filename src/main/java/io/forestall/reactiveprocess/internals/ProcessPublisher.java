package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessOutput;

import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

public class ProcessPublisher<S extends InputStream, T> implements Flow.Publisher<ProcessOutput<S, T>> {

    private final ExecutorService subscriberExecutor;

    private final ConcurrentLinkedQueue<ProcessOutput<S, T>> output;

    public ProcessPublisher() {
        subscriberExecutor = Executors.newSingleThreadExecutor();
        output = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessOutput<S, T>> subscriber) {

    }
}
