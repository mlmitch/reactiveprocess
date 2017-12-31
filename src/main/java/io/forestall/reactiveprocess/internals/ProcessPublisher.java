package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

public class ProcessPublisher implements Flow.Publisher<ProcessResult> {

    private final ExecutorService subscriberExecutor;


    public ProcessPublisher(){
        subscriberExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessResult> subscriber) {

    }
}
