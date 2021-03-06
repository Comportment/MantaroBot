package net.kodehawa.mantarobot.core.listeners;

import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.hooks.EventListener;

public abstract class OptimizedListener<T extends Event> implements EventListener {
	private transient final Class<T> tClass;

	public OptimizedListener(Class<T> tClass) {
		this.tClass = tClass;
	}

	public abstract void event(T event);

	@Override
	@SuppressWarnings("unchecked")
	public void onEvent(Event event) {
		if (tClass.isInstance(event)) event((T) event);
	}
}