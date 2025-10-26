package com.mlprograms.searchmax.model;

import lombok.Getter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class SearchModel {

    public static final String PROP_RESULTS = "results";
    public static final String PROP_STATUS = "status";
    public static final String PROP_PROGRESS = "progress";
    public static final String PROP_RESULT_ADDED = "resultAdded";
    public static final String PROP_RESULTS_BATCH = "resultsBatch";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final List<String> results = new ArrayList<>();
    private String status = "Bereit";
    private int progressDone = 0;
    private int progressTotal = 0;

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public List<String> getResults() {
        synchronized (results) {
            return Collections.unmodifiableList(new ArrayList<>(results));
        }
    }

    public void clearResults() {
        List<String> old = getResults();
        synchronized (results) {
            results.clear();
        }
        pcs.firePropertyChange(PROP_RESULTS, old, getResults());
    }

    public void addResult(String r) {
        synchronized (results) {
            results.add(r);
        }
        pcs.firePropertyChange(PROP_RESULT_ADDED, null, r);
    }

    public void addResults(java.util.Collection<String> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        List<String> old = getResults();
        synchronized (results) {
            results.addAll(incoming);
        }
        pcs.firePropertyChange(PROP_RESULTS, old, getResults());
    }

    public void addResultsBatch(List<String> batch) {
        if (batch == null || batch.isEmpty()) return;
        synchronized (results) {
            results.addAll(batch);
        }
        pcs.firePropertyChange(PROP_RESULTS_BATCH, null, new ArrayList<>(batch));
    }

    public void setStatus(String status) {
        String old = this.status;
        this.status = status;
        pcs.firePropertyChange(PROP_STATUS, old, status);
    }

    public void setProgress(int done, int total) {
        int oldDone = this.progressDone;
        int oldTotal = this.progressTotal;
        this.progressDone = done;
        this.progressTotal = total;
        pcs.firePropertyChange(PROP_PROGRESS, new int[]{oldDone, oldTotal}, new int[]{done, total});
    }

}
