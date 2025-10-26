package com.mlprograms.searchmax.service;

public interface SearchEventListener {
    void onMatch(String match);
    void onEnd(String summary);
    void onError(String message);
}
