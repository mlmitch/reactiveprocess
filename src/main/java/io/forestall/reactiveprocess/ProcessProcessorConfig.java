package io.forestall.reactiveprocess;

public class ProcessProcessorConfig {

    /**
     * Used to launch new processes.
     */
    private final ProcessBuilder processBuilder;

    /**
     * Maximum number of processes running concurrently.
     */
    private final int processLimit;

    /**
     * Number of milliseconds to wait before terminating a process.
     */
    private final long processTimeout;

    /**
     * Hard limit on the maximum number of output queue elements.
     * There will never be more than this number of outputs queued.
     */
    private final long outputQueueLimit;

    /**
     * Soft limit on the maximum number of input queue elements.
     * The number of outstanding requests plus the number of
     * queued inputs will never be greater than this number
     * under normal circumstance.
     *
     * Example of a non-normal circumstance is ProcessProcessor
     * repeatedly cancelling subscriptions and those subscriptions
     * keep calling onNext.
     */
    private final long inputQueueLimit;


    public ProcessProcessorConfig(ProcessBuilder processBuilder, int processLimit, long processTimeout, long outputQueueLimit, long inputQueueLimit) {
        if (null == processBuilder) {
            throw new IllegalArgumentException("Supplied ProcessBuilder must not be null.");
        }

        if (processLimit <= 0) {
            throw new IllegalArgumentException("Must allow at least one process to run.");
        }

        if (processTimeout < 0) {
            throw new IllegalArgumentException("Timeout must not be negative.");
        }

        if (outputQueueLimit <= 0) {
            throw new IllegalArgumentException("Must allow at least one output to queue.");
        }

        if (inputQueueLimit <= 0) {
            throw new IllegalArgumentException("Must allow at least one input to queue.");
        }

        this.processBuilder = processBuilder;
        this.processLimit = processLimit;
        this.processTimeout = processTimeout;
        this.outputQueueLimit = outputQueueLimit;
        this.inputQueueLimit = inputQueueLimit;
    }

    public ProcessBuilder getProcessBuilder() {
        return processBuilder;
    }

    public int getProcessLimit() {
        return processLimit;
    }

    public long getProcessTimeout() {
        return processTimeout;
    }

    public long getOutputQueueLimit() {
        return outputQueueLimit;
    }

    public long getInputQueueLimit() {
        return inputQueueLimit;
    }
}
