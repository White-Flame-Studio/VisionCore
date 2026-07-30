package com.xnexusacs.visioncore.common.event;

import com.xnexusacs.visioncore.common.event.events.MediaEvent;
import com.xnexusacs.visioncore.common.log.MediaLogger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class EventBus {

    private final Executor dispatchExecutor;
    private final MediaLogger logger;
    private final Map<Class<?>, List<Consumer<MediaEvent>>> listeners = new ConcurrentHashMap<>();

    public EventBus(Executor dispatchExecutor, MediaLogger logger) {
        this.dispatchExecutor = dispatchExecutor;
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public <T extends MediaEvent> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add((Consumer<MediaEvent>) listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends MediaEvent> void unsubscribe(Class<T> type, Consumer<T> listener) {
        List<Consumer<MediaEvent>> subs = listeners.get(type);

        if (subs != null) {
            subs.remove((Consumer<MediaEvent>) listener);
        }
    }

    public void post(MediaEvent event) {
        List<Consumer<MediaEvent>> subs = listeners.get(event.getClass());

        if (subs == null || subs.isEmpty()) {
            return;
        }

        dispatchExecutor.execute(() -> {
            for (Consumer<MediaEvent> listener : subs) {
                try {
                    listener.accept(event);
                } catch (RuntimeException e) {
                    logger.error("Listener " + event.getClass().getSimpleName() + " failed with an exception", e);
                }
            }
        });
    }
}
