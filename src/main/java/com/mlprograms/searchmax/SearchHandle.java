package com.mlprograms.searchmax;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@RequiredArgsConstructor
public final class SearchHandle {

    private volatile ForkJoinTask<?> task;
    private final Collection<ForkJoinTask<?>> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final long startNano;
    private final AtomicInteger remainingTasks;
    private final AtomicInteger matchCount;
    private final Collection<String> results;

    public SearchHandle(final ForkJoinTask<?> task, final long startTimeNano, final AtomicInteger remainingTasks, final AtomicInteger matchCount, final Collection<String> results) {
        this.startNano = startTimeNano;
        this.remainingTasks = remainingTasks;
        this.matchCount = matchCount;
        this.results = results;
        setTask(task);
    }

    public void setTask(ForkJoinTask<?> task) {
        this.task = task;
        addTask(task);
    }

    public void addTask(ForkJoinTask<?> task) {
        if (task != null) {
            tasks.add(task);
        }
    }

}
