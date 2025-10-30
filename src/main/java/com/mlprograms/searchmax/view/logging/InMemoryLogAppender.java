package com.mlprograms.searchmax.view.logging;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

@Plugin(
        name = "InMemoryLogAppender",
        category = "Core",
        elementType = Appender.ELEMENT_TYPE,
        printObject = true
)
public final class InMemoryLogAppender extends AbstractAppender {

    private static final int DEFAULT_BUFFER_CAPACITY = 1000;
    private static final String PLUGIN_NAME = "InMemoryLogAppender";

    private final LinkedBlockingDeque<String> logBuffer = new LinkedBlockingDeque<>(DEFAULT_BUFFER_CAPACITY);
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();

    private InMemoryLogAppender(
            final String name,
            final Filter filter,
            final Layout<? extends Serializable> layout
    ) {
        super(name, filter, layout, false, null);
    }

    @Override
    public void append(final LogEvent logEvent) {
        if (logEvent == null) {
            return;
        }

        final String formattedLogMessage = formatLogMessage(logEvent);
        if (formattedLogMessage == null || formattedLogMessage.isEmpty()) {
            return;
        }

        addMessageToBuffer(formattedLogMessage);
        notifyAllListeners(formattedLogMessage);
    }

    private String formatLogMessage(final LogEvent logEvent) {
        if (getLayout() != null) {
            return new String(getLayout().toByteArray(logEvent), StandardCharsets.UTF_8);
        } else {
            return logEvent.getMessage().getFormattedMessage();
        }
    }

    private void addMessageToBuffer(final String logMessage) {
        while (!logBuffer.offerLast(logMessage)) {
            logBuffer.pollFirst();
        }
    }

    private void notifyAllListeners(final String logMessage) {
        for (final Consumer<String> listener : logListeners) {
            try {
                listener.accept(logMessage);
            } catch (final Exception exception) {
                // Ignorieren von Exceptions in Listenern, um andere Listener nicht zu beeinträchtigen
            }
        }
    }

    public List<String> getAllLogMessages() {
        return Collections.unmodifiableList(new ArrayList<>(logBuffer));
    }

    public void addListener(final Consumer<String> listener) {
        if (listener != null) {
            logListeners.add(listener);
        }
    }

    public void removeListener(final Consumer<String> listener) {
        if (listener != null) {
            logListeners.remove(listener);
        }
    }

    public void clearLogBuffer() {
        logBuffer.clear();
    }

    public int getLogMessageCount() {
        return logBuffer.size();
    }

    public int getBufferCapacity() {
        return DEFAULT_BUFFER_CAPACITY;
    }

    public boolean isBufferFull() {
        return logBuffer.remainingCapacity() == 0;
    }

    @PluginFactory
    public static InMemoryLogAppender createAppender(
            @PluginAttribute("name") @Required final String name,
            @PluginElement("Layout") final Layout<? extends Serializable> layout,
            @PluginElement("Filter") final Filter filter
    ) {
        return new InMemoryLogAppender(name, filter, layout);
    }

}