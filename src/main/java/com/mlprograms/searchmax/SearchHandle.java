package com.mlprograms.searchmax;

import java.util.Collection;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;

public final class SearchHandle {

    private volatile ForkJoinTask<?> task;
    private final long startNano;
    private final AtomicInteger remainingTasks;
    private final AtomicInteger matchCount;
    private final Collection<String> results;

    public SearchHandle(ForkJoinTask<?> task, long startNano, AtomicInteger remainingTasks, AtomicInteger matchCount, Collection<String> results) {
        this.task = task;
        this.startNano = startNano;
        this.remainingTasks = remainingTasks;
        this.matchCount = matchCount;
        this.results = results;
    }

    public ForkJoinTask<?> getTask() {
        return task;
    }

    public void setTask(ForkJoinTask<?> task) {
        this.task = task;
    }

    public long getStartNano() {
        return startNano;
    }

    public AtomicInteger getRemainingTasks() {
        return remainingTasks;
    }

    public AtomicInteger getMatchCount() {
        return matchCount;
    }

    public Collection<String> getResults() {
        return results;
    }
}

