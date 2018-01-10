package io.forestall.reactiveprocess;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.*;

import static java.lang.Long.max;

public class ProcessProcessor<T> implements Flow.Processor<ProcessInput<T>, ProcessOutput<T>> {

    private final ProcessProcessorConfig config;

    //single thread executor for bookeeping
    private final ExecutorService executor;

    private boolean destroyed;

    private Flow.Subscription incomingSubscription;
    private final ConcurrentLinkedQueue<ProcessInput<T>> input;
    //keep our own input size as ConcurrentLinkedQueue::size is O(n) and not accurate
    private long inputSize;
    private long outstandingRequests;

    private int runningProcesses;
    private final ExecutorService streamCopier;

    private final ConcurrentLinkedQueue<ProcessOutput<T>> output;
    private long outputSize;

    private final ConcurrentHashMap<Flow.Subscriber<? super ProcessOutput<T>>, ProcessSubscription<T>> outgoingSubscriptions;

    public ProcessProcessor(ProcessProcessorConfig config) {
        this.config = config;
        executor = Executors.newSingleThreadExecutor();
        destroyed = false;

        this.incomingSubscription = null;
        input = new ConcurrentLinkedQueue<>();
        inputSize = 0;
        outstandingRequests = 0;

        streamCopier = Executors.newWorkStealingPool(this.config.getProcessLimit());
        runningProcesses = 0;

        output = new ConcurrentLinkedQueue<>();
        outputSize = 0;

        outgoingSubscriptions = new ConcurrentHashMap<>();
    }

    @Override
    public void onNext(ProcessInput<T> item) {
        if (null == item) {
            throw new NullPointerException("Null InputStreams not allowed.");
        }

        //always true for this type of queue
        input.add(item);

        //book keeping
        executor.submit(() -> {
            outstandingRequests--;
            inputSize++;
            pullToSubscriber();

            //something has become available
            pullToProcess();
        });
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (null == subscription) {
            throw new NullPointerException("Null Subscriptions not allowed.");
        }

        executor.submit(() -> {
            if (null == this.incomingSubscription && !destroyed) {
                this.incomingSubscription = subscription;
                outstandingRequests = 0;
                pullToSubscriber();
            } else {
                subscription.cancel();
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
        if (null == throwable) {
            throw new NullPointerException("Null Throwables not allowed.");
        }

        //TODO: consider propagating the error... doesn't seem to be a good reason to

        //Outstanding requests will be reset when
        //another subscriber is provided
        //This needs to be submitted or incomingSubscription would need to be volatile
        executor.submit(() -> {
            //if (null != incomingSubscription)
            incomingSubscription = null;
        });
    }

    @Override
    public void onComplete() {
        //Outstanding requests will be reset when
        //another subscriber is provided
        executor.submit(() -> {
            //if (null != incomingSubscription)
            incomingSubscription = null;
        });
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessOutput<T>> subscriber) {
        if (null == subscriber) {
            throw new NullPointerException("Null subscribers not allowed");
        }

        //if we already have this subscriber then just don't create another incomingSubscription
        //goes against spec to create another
        //not mandated to notify the subscriber
        outgoingSubscriptions.computeIfAbsent(subscriber, s -> {
            ProcessSubscription<T> subscription = new ProcessSubscription<>(s, executor, this::supplyFromProducer);
            s.onSubscribe(subscription);
            return subscription;
        });
    }

    public void subscriberUnsubscribe() {
        executor.submit(() -> {
            if (null != incomingSubscription) {
                incomingSubscription.cancel();
                incomingSubscription = null;
            }
        });
    }

    //only call from within executor
    private void pullToSubscriber() {
        //don't strictly enforce input size
        //just make sure the queue is being consumed.
        if (outstandingRequests + inputSize < config.getInputQueueLimit() && null != incomingSubscription) {
            //if outstandingRequests goes negative due to previous
            //publishers, we recover here
            outstandingRequests = config.getInputQueueLimit() - inputSize;
            long desired = config.getInputQueueLimit() - inputSize - max(0, outstandingRequests);
            if (desired > 0) {
                incomingSubscription.request(desired);
            }
        }
    }

    //only call from within executor
    private Optional<ProcessInput<T>> supplyFromSubscriber() {
        Optional<ProcessInput<T>> result = Optional.ofNullable(input.poll());

        if (result.isPresent()) {
            //successfully got an element
            inputSize--;
            pullToSubscriber();
        }

        return result;
    }

    //only call within executor
    private void pullToProcess() {
        //check if we want to make another process
        //max processes executing already
        //result queue can take more
        if (runningProcesses < config.getProcessLimit() && outputSize < config.getOutputQueueLimit()) {
            Optional<ProcessOutput<T>> processOutput = supplyFromSubscriber().flatMap(this::process);
            if (processOutput.isPresent()) {
                output.add(processOutput.get());
                outputSize++;
                pushToSubscriptions();
            }
        }

    }

    //only call within executor
    private Optional<ProcessOutput<T>> process(ProcessInput<T> processInput) {
        Process process;

        try {
            process = config.getProcessBuilder().start();
        } catch (IOException e) {
            //if we can't launch processes then what are we doing
            //destroy(e);
            return Optional.empty();
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

        process.toHandle()
                .onExit()
                .orTimeout(config.getProcessTimeout(), TimeUnit.MILLISECONDS)
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
        ProcessOutput<T> processOutput =
                new ProcessOutput<>(process.toHandle(),
                        new BufferedInputStream(process.getInputStream()),
                        new BufferedInputStream(process.getErrorStream()),
                        processInput.getTag());
        return Optional.of(processOutput);
    }

    //only call within executor
    private void pushToSubscriptions() {
        //remove the inactive subscriptions
        outgoingSubscriptions.entrySet().stream()
                .filter(e -> !e.getValue().isActive())
                .forEach(e -> outgoingSubscriptions.remove(e.getKey(), e.getValue()));

        //initiate pushes to all the active outgoingSubscriptions
        //would be nice if we could do this in a random order
        outgoingSubscriptions.values().stream()
                .filter(ProcessSubscription::isActive)
                .forEach(ProcessSubscription::pushToSubscription);
    }

    //only call from within executor
    //called from inside outgoingSubscriptions.
    //initiates a pull as we might need more outputs
    private Optional<ProcessOutput<T>> supplyFromProducer() {
        Optional<ProcessOutput<T>> result = Optional.ofNullable(output.poll());

        if (result.isPresent()) {
            //successfully got an element
            outputSize--;
            pullToProcess();
        }

        return result;
    }
}