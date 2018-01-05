package io.forestall.reactiveprocess;

import io.forestall.reactiveprocess.internals.ProcessSubscriber;
import io.forestall.reactiveprocess.internals.ProcessSubscription;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.*;

public class ProcessProcessor<T>
        implements Flow.Processor<ProcessInput<T>, ProcessOutput<T>> {

    private final ProcessSubscriber<T> processSubscriber;
    private final ConcurrentHashMap<Flow.Subscriber<? super ProcessOutput<T>>, ProcessSubscription<T>> subscriptions;

    private final ProcessBuilder processBuilder;
    private final int maxProcesses;
    private int runningProcesses;

    private final ConcurrentLinkedQueue<ProcessOutput<T>> output;
    private long outputSize;
    private final long maxOutputSize;

    //single thread executor for bookeeping
    private final ExecutorService executor;

    private final long processTimeoutMillis;

    private final ExecutorService streamCopier;

    public ProcessProcessor(ProcessBuilder processBuilder, int maxProcesses, long maxRequests, long processTimeoutMillis) {
        if (maxProcesses <= 0) {
            throw new IllegalArgumentException("Maximum number of processes must be greater than 0");
        }

        if (null == processBuilder) {
            throw new IllegalArgumentException("Null ProcessBuilder not allowed");
        }

        this.processBuilder = processBuilder;
        this.maxProcesses = maxProcesses;

        executor = Executors.newSingleThreadExecutor();

        processSubscriber = new ProcessSubscriber<>(maxRequests, executor, this::pull);

        runningProcesses = 0;
        this.processTimeoutMillis = processTimeoutMillis;

        subscriptions = new ConcurrentHashMap<>();

        output = new ConcurrentLinkedQueue<>();
        outputSize = 0;
        maxOutputSize = maxRequests;

        streamCopier = Executors.newWorkStealingPool(maxProcesses);
    }

    private void push() {
        //initiate pushes to all the subscriptions
        //would be nice if we could do this in a random order
        subscriptions.values().forEach(ProcessSubscription::push);
    }

    public void destroy() {
        //cancels incoming subscription
        //and signals oncomplete
        processSubscriber.destroy();
        subscriptions.values().forEach(ProcessSubscription::pushComplete);
    }

    private void destroy(Throwable throwable) {
        //cancels incoming subscription
        //and pushes out the error
        processSubscriber.destroy();
        subscriptions.values().forEach(s -> s.pushError(throwable));
    }

    private void pull() {
        //called by the subscriber on the executor

        //check if we want to make another process
        //max processes executing already
        //result queue can take more
        if (runningProcesses < maxProcesses && outputSize < maxOutputSize) {
            Optional<ProcessInput<T>> input = processSubscriber.supply();
            input.ifPresent(this::launchProcess);
        }

    }

    //only call within processExecutor
    private void launchProcess(ProcessInput<T> processInput) {
        Process process;

        try {
            process = processBuilder.start();
        } catch (IOException e) {
            //if we can't launch processes then what are we doing
            destroy(e);
            return;
        }

        runningProcesses++;

        InputStream stdinIS = processInput.getStdin();
        //docs say this should be buffered
        OutputStream stdinOS = process.getOutputStream();

        streamCopier.submit(() -> {
            try {
                long transferredBytes = stdinIS.transferTo(new BufferedOutputStream(stdinOS));
                //TODO: stuff this value into the process output
                stdinOS.flush();
            } catch (IOException e) {
                //TODO: what to do with this failure.. should make some noise, but doesn't warrant an onError
            } finally {
                //want to try both separately
                try {
                    stdinOS.close();
                } catch (IOException e) {
                    //TODO: what could we possibly do here.. just make some noise?
                }
                try {
                    stdinIS.close();
                } catch (IOException e) {
                    //TODO: what could we possibly do here.. just make some noise?
                }
            }
        });

        ProcessOutput<T> processOutput =
                new ProcessOutput<>(process.toHandle(),
                        new BufferedInputStream(process.getInputStream()),
                        new BufferedInputStream(process.getErrorStream()),
                        processInput.getTag());

        output.add(processOutput);
        outputSize++;

        process.toHandle()
                .onExit()
                .orTimeout(processTimeoutMillis, TimeUnit.MILLISECONDS)
                .whenCompleteAsync((p, t) -> {
                    if (p.isAlive()) {
                        //this means we timed out
                        //destroy instead of forciblyDestroy
                        //to give the process some chance of cleaning
                        p.destroy();
                    }

                    //if(null != t){
                    //TODO: do something with this error. maybe a logging function.
                    //}

                }, executor);

        //TODO: set up actions to take when the process completes
    }

    //called from inside subscriptions.
    //initiates a pull as we might need more outputs
    private Optional<ProcessOutput<T>> supply() {
        Optional<ProcessOutput<T>> result = Optional.ofNullable(output.poll());

        if (result.isPresent()) {
            //successfully got an element
            executor.submit(() -> {
                outputSize--;
                pull();
            });
        }

        return result;
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    //Producer method
    ////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessOutput<T>> subscriber) {
        if (null == subscriber) {
            throw new NullPointerException("Null subscribers not allowed");
        }

        //if we already have this subscriber then just don't create another subscription
        //goes against spec to create another
        //not mandated to notify the subscriber
        subscriptions.computeIfAbsent(subscriber, s -> {
            ProcessSubscription<T> subscription = new ProcessSubscription<>(s, executor, this::supply);
            s.onSubscribe(subscription);
            return subscription;
        });
    }


    ////////////////////////////////////////////////////////////////////////////////////////
    //Subscriber methods
    ////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        processSubscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(ProcessInput<T> item) {
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