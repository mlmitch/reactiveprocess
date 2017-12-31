package io.forestall.reactiveprocess;

import io.forestall.reactiveprocess.internals.ProcessPublisher;
import io.forestall.reactiveprocess.internals.ProcessSubscriber;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Flow;

public class ProcessProcessor implements Flow.Processor<InputStream, ProcessResult> {

    private final ProcessSubscriber processSubscriber;
    private final ProcessPublisher processPublisher;

    //TODO: maintain some sort of shutdown state where we reject future subscriptions?

    public ProcessProcessor(List<String> arguments, int maxProcesses) {
        processSubscriber = new ProcessSubscriber();
        processPublisher = new ProcessPublisher();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessResult> subscriber) {
        processPublisher.subscribe(subscriber);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        processSubscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(InputStream item) {
        processSubscriber.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        processSubscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        processSubscriber.onComplete();
    }
}