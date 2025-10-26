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

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final List<String> results = new ArrayList<>();
    private String status = "Bereit";

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
        List<String> old = getResults();
        synchronized (results) {
            results.add(r);
        }
        pcs.firePropertyChange(PROP_RESULTS, old, getResults());
    }

    public void setStatus(String status) {
        String old = this.status;
        this.status = status;
        pcs.firePropertyChange(PROP_STATUS, old, status);
    }

}
