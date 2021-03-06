package net.kodehawa.mantarobot.core.listeners;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.util.function.BiPredicate;

/**
 * @deprecated use {@link net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations} now
 */
@Deprecated public class FunctionListener extends OptimizedListener<GuildMessageReceivedEvent> {
	private final BiPredicate<FunctionListener, GuildMessageReceivedEvent> eventConsumer;
	private final String targetChannel;
	private boolean isDone = false;

	public FunctionListener(String targetChannel, BiPredicate<FunctionListener, GuildMessageReceivedEvent> eventConsumer) {
		super(GuildMessageReceivedEvent.class);
		this.targetChannel = targetChannel;
		this.eventConsumer = eventConsumer;
	}

	@Override
	public void event(GuildMessageReceivedEvent event) {
		if (event.getChannel().getId().equals(targetChannel) && eventConsumer.test(this, event)) {
			isDone = true;
			event.getJDA().removeEventListener(this);
		}
	}

	public boolean isDone() {
		return isDone;
	}
}
