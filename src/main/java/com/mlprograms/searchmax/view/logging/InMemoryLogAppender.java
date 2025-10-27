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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/**
 * Ein einfacher In-Memory Log4j2 Appender, der Log-Zeilen in einem begrenzten
 * Queue speichert und Listener bei neuen Einträgen informiert.
 */
@Plugin(name = "InMemoryLogAppender", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class InMemoryLogAppender extends AbstractAppender {

    private static final int DEFAULT_CAPACITY = 1000;
    private final LinkedBlockingDeque<String> buffer = new LinkedBlockingDeque<>(DEFAULT_CAPACITY);
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    protected InMemoryLogAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout, false, null);
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) return;
        final String message = getLayout() != null ? new String(getLayout().toByteArray(event)) : event.getMessage().getFormattedMessage();
        if (message == null) return;
        // Bounded: entferne älteste Einträge, wenn voll
        while (!buffer.offerLast(message)) {
            buffer.pollFirst();
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(message);
            } catch (Exception ignored) {
            }
        }
    }

    public List<String> getAll() {
        return new ArrayList<>(buffer);
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /**
     * Löscht alle gespeicherten Log-Einträge im Puffer.
     */
    public void clear() {
        buffer.clear();
    }

    @PluginFactory
    public static InMemoryLogAppender createAppender(@PluginAttribute("name") @Required String name,
                                                      @PluginElement("Layout") Layout<? extends Serializable> layout,
                                                      @PluginElement("Filter") final Filter filter) {
        return new InMemoryLogAppender(name, filter, layout);
    }
}
