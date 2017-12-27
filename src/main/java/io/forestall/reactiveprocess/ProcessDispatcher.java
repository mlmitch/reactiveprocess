package io.forestall.reactiveprocess;

import java.io.InputStream;
import java.util.concurrent.Flow;

public class ProcessDispatcher implements Flow.Processor<InputStream, ProcessResult> {

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessResult> subscriber) {

    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {

    }

    @Override
    public void onNext(InputStream item) {

    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
}