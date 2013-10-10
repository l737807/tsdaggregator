package com.arpnetworking.tsdaggregator.publishing;

import com.arpnetworking.tsdaggregator.AggregatedData;

import java.util.ArrayList;

/**
 * A publisher that wraps multiple others and publishes to all of them.
 *
 * @author barp
 */
public class MultiPublisher implements AggregationPublisher {
    private ArrayList<AggregationPublisher> _listeners = new ArrayList<AggregationPublisher>();

    public void addListener(AggregationPublisher listener) {
        _listeners.add(listener);
    }

    @Override
    public void recordAggregation(AggregatedData[] data) {
        for (AggregationPublisher listener : _listeners) {
            listener.recordAggregation(data);
        }
    }

    @Override
    public void close() {
        for (AggregationPublisher listener : _listeners) {
            listener.close();
        }
    }
}