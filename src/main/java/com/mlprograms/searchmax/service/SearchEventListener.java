package com.mlprograms.searchmax.service;

public interface SearchEventListener {
    void onId(String id);
    void onMatch(String match);
    void onEnd(String summary);
    void onError(String message);
}

