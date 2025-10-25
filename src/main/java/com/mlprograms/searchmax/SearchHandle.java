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

    public SearchHandle(ForkJoinTask<?> initialTask, long startNano, AtomicInteger remainingTasks, AtomicInteger matchCount, Collection<String> results) {
        this.startNano = startNano;
        this.remainingTasks = remainingTasks;
        this.matchCount = matchCount;
        this.results = results;
        setTask(initialTask);
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
