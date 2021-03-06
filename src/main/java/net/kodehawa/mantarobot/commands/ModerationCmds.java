package net.kodehawa.mantarobot.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.moderation.ModLog;
import net.kodehawa.mantarobot.commands.rpg.TextChannelGround;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.data.db.ManagedDatabase;
import net.kodehawa.mantarobot.data.entities.DBGuild;
import net.kodehawa.mantarobot.data.entities.helpers.GuildData;
import net.kodehawa.mantarobot.modules.Category;
import net.kodehawa.mantarobot.modules.CommandPermission;
import net.kodehawa.mantarobot.modules.Module;
import net.kodehawa.mantarobot.modules.SimpleCommand;
import net.kodehawa.mantarobot.utils.DiscordUtils;
import net.kodehawa.mantarobot.utils.StringUtils;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ModerationCmds extends Module {
	private static final Logger LOGGER = LoggerFactory.getLogger("osu!");
	private static final Pattern pattern = Pattern.compile("\\d+?[a-zA-Z]");

	public ModerationCmds() {
		super(Category.MODERATION);
		ban();
		kick();
		tempban();
		opts();
		prune();
	}

	private void tempban(){
		super.register("tempban", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				String reason = content;
				Guild guild = event.getGuild();
				User author = event.getAuthor();
				TextChannel channel = event.getChannel();
				Message receivedMessage = event.getMessage();

				if (!guild.getMember(author).hasPermission(net.dv8tion.jda.core.Permission.BAN_MEMBERS)) {
					channel.sendMessage(EmoteReference.ERROR + "Cannot ban: You have no Ban Members permission.").queue();
					return;
				}


				if (event.getMessage().getMentionedUsers().isEmpty()) {
					event.getChannel().sendMessage(EmoteReference.ERROR  + "You need to mention an user!").queue();
					return;
				}

				for (User user : event.getMessage().getMentionedUsers()) {
					reason = reason.replaceAll("(\\s+)?<@!?" + user.getId() + ">(\\s+)?", "");
				}
				int index = reason.indexOf("time:");
				if (index < 0) {
					event.getChannel().sendMessage(EmoteReference.ERROR +
							"You cannot temp ban an user without giving me the time!").queue();
					return;
				}
				String time = reason.substring(index);
				reason = reason.replace(time, "").trim();
				time = time.replaceAll("time:(\\s+)?", "");
				if (reason.isEmpty()) {
					event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot temp ban someone without a reason.!").queue();
					return;
				}

				if (time.isEmpty()) {
					event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot temp ban someone without giving me the time!").queue();
					return;
				}

				final DBGuild db = MantaroData.db().getGuild(event.getGuild());
				long l = parse(time);
				String finalReason = reason;
				String sTime = StringUtils.parseTime(l);
				receivedMessage.getMentionedUsers().forEach(user -> {
					user.openPrivateChannel().complete().sendMessage(EmoteReference.MEGA + "You were **temporarly banned** by " + event.getAuthor().getName() + "#"
							+ event.getAuthor().getDiscriminator() + " with reason: " + finalReason + ".").queue();
					db.getData().setCases(db.getData().getCases() + 1);
					db.saveAsync();
					channel.sendMessage(EmoteReference.ZAP + "You will be missed... or not " + event.getMember().getEffectiveName()).queue();
					ModLog.log(event.getMember(), user, finalReason, ModLog.ModAction.TEMP_BAN, db.getData().getCases(), sTime);
					MantaroBot.getInstance().getTempBanManager().addTempban(
							guild.getId() + ":" + user.getId(), l + System.currentTimeMillis());
					TextChannelGround.of(event).dropItemWithChance(1, 2);
				});
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Tempban Command")
						.setDescription("Temporarly bans an user")
						.addField("Usage", "~>tempban <user> <reason> time:<time>", false)
						.addField("Example", "~>tempban @Kodehawa example time:1d", false)
						.addField("Extended usage", "time: can be used with the following parameters: " +
								"d (days), s (second), m (minutes), h (hour). For example time:1d1h will give a day and an hour.", false)
						.build();
			}
		});
	}

	private void ban() {
		super.register("ban", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				Guild guild = event.getGuild();
				User author = event.getAuthor();
				TextChannel channel = event.getChannel();
				Message receivedMessage = event.getMessage();
				String reason = content;

				if (!guild.getMember(author).hasPermission(net.dv8tion.jda.core.Permission.BAN_MEMBERS)) {
					channel.sendMessage(EmoteReference.ERROR + "Cannot ban: You have no Ban Members permission.").queue();
					return;
				}

				if (receivedMessage.getMentionedUsers().isEmpty()) {
					channel.sendMessage(EmoteReference.ERROR + "You need to mention at least one user to ban.").queue();
					return;
				}

				for (User user : event.getMessage().getMentionedUsers()) {
					reason = reason.replaceAll("(\\s+)?<@!?" + user.getId() + ">(\\s+)?", "");
				}

				if(reason.isEmpty()){
					reason = "Not specified";
				}

				final String finalReason = reason;

				receivedMessage.getMentionedUsers().forEach(user -> {
					if(!event.getGuild().getMember(event.getAuthor()).canInteract(event.getGuild().getMember(user))){
						event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot ban an user in a higher hierarchy than you").queue();
						return;
					}

					if(event.getAuthor().getId().equals(user.getId())){
						event.getChannel().sendMessage(EmoteReference.ERROR + "Why are you trying to ban yourself?").queue();
						return;
					}

					Member member = guild.getMember(user);
					if (member == null) return;
					if (!guild.getSelfMember().canInteract(member)) {
						channel.sendMessage(EmoteReference.ERROR + "Cannot ban member " + member.getEffectiveName() + ", they are higher or the same " + "hierachy than I am!").queue();
						return;
					}

					if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.core.Permission.BAN_MEMBERS)) {
						channel.sendMessage(EmoteReference.ERROR + "Sorry! I don't have permission to ban members in this server!").queue();
						return;
					}
					final DBGuild db = MantaroData.db().getGuild(event.getGuild());

					guild.getController().ban(member, 7).queue(
						success -> {
							user.openPrivateChannel().complete().sendMessage(EmoteReference.MEGA + "You were **banned** by " + event.getAuthor().getName() + "#"
									+ event.getAuthor().getDiscriminator() + " with reason: " + finalReason + ".").queue();
							db.getData().setCases(db.getData().getCases() + 1);
							db.saveAsync();
							channel.sendMessage(EmoteReference.ZAP + "You will be missed... or not " + member.getEffectiveName()).queue();
							ModLog.log(event.getMember(), user, finalReason, ModLog.ModAction.BAN, db.getData().getCases());
							TextChannelGround.of(event).dropItemWithChance(1, 2);
						},
						error ->
						{
							if (error instanceof PermissionException) {
								channel.sendMessage(EmoteReference.ERROR + "Error banning " + member.getEffectiveName()
									+ ": " + "(No permission provided: " + ((PermissionException) error).getPermission() + ")").queue();
							} else {
								channel.sendMessage(EmoteReference.ERROR + "Unknown error while banning " + member.getEffectiveName()
									+ ": " + "<" + error.getClass().getSimpleName() + ">: " + error.getMessage()).queue();

								LOGGER.warn("Unexpected error while banning someone.", error);
							}
						});
				});
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Ban")
					.setDescription("Bans the mentioned users.")
					.build();
			}

			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.USER;
			}

		});
	}

	private void kick() {
		super.register("kick", new SimpleCommand() {
			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.USER;
			}

			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				Guild guild = event.getGuild();
				User author = event.getAuthor();
				TextChannel channel = event.getChannel();
				Message receivedMessage = event.getMessage();
				String reason = content;

				if (!guild.getMember(author).hasPermission(net.dv8tion.jda.core.Permission.KICK_MEMBERS)) {
					channel.sendMessage(EmoteReference.ERROR2 + "Cannot kick: You have no Kick Members permission.").queue();
					return;
				}

				if (receivedMessage.getMentionedUsers().isEmpty()) {
					channel.sendMessage(EmoteReference.ERROR + "You must mention 1 or more users to be kicked!").queue();
					return;
				}

				Member selfMember = guild.getSelfMember();

				if (!selfMember.hasPermission(net.dv8tion.jda.core.Permission.KICK_MEMBERS)) {
					channel.sendMessage(EmoteReference.ERROR2 + "Sorry! I don't have permission to kick members in this server!").queue();
					return;
				}

				for (User user : event.getMessage().getMentionedUsers()) {
					reason = reason.replaceAll("(\\s+)?<@!?" + user.getId() + ">(\\s+)?", "");
				}

				if(reason.isEmpty()){
					reason = "Not specified";
				}

				final String finalReason = reason;

				receivedMessage.getMentionedUsers().forEach(user -> {
					if(!event.getGuild().getMember(event.getAuthor()).canInteract(event.getGuild().getMember(user))){
						event.getChannel().sendMessage(EmoteReference.ERROR + "You cannot kick an user in a higher hierarchy than you").queue();
						return;
					}

					if(event.getAuthor().getId().equals(user.getId())){
						event.getChannel().sendMessage(EmoteReference.ERROR + "Why are you trying to kick yourself?").queue();
						return;
					}

					Member member = guild.getMember(user);
					if (member == null) return;

					//If one of them is in a higher hierarchy than the bot, cannot kick.
					if (!selfMember.canInteract(member)) {
						channel.sendMessage(EmoteReference.ERROR2 + "Cannot kick member: " + member.getEffectiveName() + ", they are higher or the same " + "hierachy than I am!").queue();
						return;
					}
					final DBGuild db = MantaroData.db().getGuild(event.getGuild());

					//Proceed to kick them. Again, using queue so I don't get rate limited.
					guild.getController().kick(member).queue(
						success -> {
							user.openPrivateChannel().complete().sendMessage(EmoteReference.MEGA + "You were **banned** by " + event.getAuthor().getName() + "#"
									+ event.getAuthor().getDiscriminator() + " with reason: " + finalReason + ".").queue();
							db.getData().setCases(db.getData().getCases() + 1);
							db.saveAsync();
							channel.sendMessage(EmoteReference.ZAP + "You will be missed... or not " + member.getEffectiveName()).queue(); //Quite funny, I think.
							ModLog.log(event.getMember(), user, finalReason, ModLog.ModAction.KICK, db.getData().getCases());
							TextChannelGround.of(event).dropItemWithChance(2, 2);
						},
						error -> {
							if (error instanceof PermissionException) {
								channel.sendMessage(String.format(EmoteReference.ERROR + "Error kicking [%s]: (No permission provided: %s)", member.getEffectiveName(), ((PermissionException) error).getPermission())).queue();
							} else {
								channel.sendMessage(String.format(EmoteReference.ERROR + "Unknown error while kicking [%s]: <%s>: %s", member.getEffectiveName(), error.getClass().getSimpleName(), error.getMessage())).queue();
								LOGGER.warn("Unexpected error while kicking someone.", error);
							}
						});
				});
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Kick")
					.setDescription("Kicks the mentioned users.")
					.build();
			}
		});
	}

	private void opts() {
		super.register("opts", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				if (args.length < 1) {
					onHelp(event);
					return;
				}

				String option = args[0];
				DBGuild dbGuild = MantaroData.db().getGuild(event.getGuild());
				GuildData guildData = dbGuild.getData();

				if (option.equals("resetmoney")) {
					//TODO guildData.users.clear();
					dbGuild.save();
					event.getChannel().sendMessage(EmoteReference.CORRECT + " Local Guild Money cleared.").queue();
					return;
				}

				if (args.length < 2) {
					onHelp(event);
					return;
				}

				String action = args[1];

				if (option.equals("logs")) {
					if (action.equals("enable")) {
						if (args.length < 3) {
							onHelp(event);
							return;
						}

						String logChannel = args[2];
						boolean isId = args[2].matches("^[0-9]*$");
						String id = isId ? logChannel : event.getGuild().getTextChannelsByName(logChannel, true).get(0).getId();
						guildData.setGuildLogChannel(id);
						dbGuild.saveAsync();
						event.getChannel().sendMessage(String.format(EmoteReference.MEGA + "Message logging enabled on this server with parameters -> ``Channel #%s (%s)``",
							logChannel, id)).queue();
						return;
					}

					if (action.equals("disable")) {
						guildData.setGuildLogChannel(null);
						dbGuild.saveAsync();
						event.getChannel().sendMessage(EmoteReference.MEGA + "Message logging disabled on this server.").queue();
						return;
					}

					onHelp(event);
					return;
				}

				if (option.equals("prefix")) {
					if (action.equals("set")) {
						if (args.length < 3) {
							onHelp(event);
							return;
						}

						String prefix = args[2];
						guildData.setGuildCustomPrefix(prefix);
						dbGuild.save();
						event.getChannel().sendMessage(EmoteReference.MEGA + "Guild custom prefix set to " + prefix).queue();
						return;
					}

					if (action.equals("clear")) {
						guildData.setGuildCustomPrefix(null);
						dbGuild.save();
						event.getChannel().sendMessage(EmoteReference.MEGA + "Guild custom prefix disabled	").queue();
						return;
					}
					onHelp(event);
					return;
				}

				if (option.equals("nsfw")) {
					if (action.equals("toggle")) {
						if (guildData.getGuildUnsafeChannels().contains(event.getChannel().getId())) {
							List<String> unsafeChannels = new ArrayList<>(guildData.getGuildUnsafeChannels());
							unsafeChannels.remove(event.getChannel().getId());
							guildData.setGuildUnsafeChannels(unsafeChannels);
							event.getChannel().sendMessage(EmoteReference.CORRECT + "NSFW in this channel has been disabled").queue();
							dbGuild.saveAsync();
							return;
						}

						List<String> unsafeChannels = new ArrayList<>(guildData.getGuildUnsafeChannels());
						unsafeChannels.add(event.getChannel().getId());
						guildData.setGuildUnsafeChannels(unsafeChannels);
						dbGuild.saveAsync();
						event.getChannel().sendMessage(EmoteReference.CORRECT + "NSFW in this channel has been enabled.").queue();
						return;
					}

					onHelp(event);
					return;
				}

				if(option.equals("devaluation")){
					if (args.length < 1) {
						onHelp(event);
						return;
					}

					if(action.equals("enable")){
						guildData.setRpgDevaluation(true);
						event.getChannel().sendMessage(EmoteReference.ERROR + "Enabled currency devaluation on this server.").queue();
						return;
					}

					if(action.equals("disable")){
						guildData.setRpgDevaluation(true);
						event.getChannel().sendMessage(EmoteReference.ERROR + "Disabled currency devaluation on this server.").queue();
						return;
					}
					dbGuild.saveAsync();
					return;
				}

				if (option.equals("birthday")) {
					if (action.equals("enable")) {
						if (args.length < 4) {
							onHelp(event);
							return;
						}
						try {
							String channel = args[2];
							String role = args[3];

							boolean isId = channel.matches("^[0-9]*$");
							String channelId = isId ? channel : event.getGuild().getTextChannelsByName(channel, true).get(0).getId();
							String roleId = event.getGuild().getRolesByName(role.replace(channelId, ""), true).get(0).getId();
							guildData.setBirthdayChannel(channelId);
							guildData.setBirthdayRole(roleId);
							dbGuild.save();
							event.getChannel().sendMessage(
								String.format(EmoteReference.MEGA + "Birthday logging enabled on this server with parameters -> Channel: ``#%s (%s)`` and role: ``%s (%s)``",
									channel, channelId, role, roleId)).queue();
							return;
						} catch (Exception e) {
							if (e instanceof IndexOutOfBoundsException) {
								event.getChannel().sendMessage(EmoteReference.ERROR + "Nothing found on channel or role.\n " +
									"**Remember, you don't have to mention neither the role or the channel, rather just type its name, order is <channel> <role>, without the leading \"<>\".**")
									.queue();
								return;
							}
							event.getChannel().sendMessage(EmoteReference.ERROR + "Wrong command arguments.").queue();
							onHelp(event);
							return;
						}
					}

					if (action.equals("disable")) {
						guildData.setBirthdayChannel(null);
						guildData.setBirthdayRole(null);
						dbGuild.save();
						event.getChannel().sendMessage(EmoteReference.MEGA + "Birthday logging disabled on this server").queue();
						return;
					}

					onHelp(event);
					return;
				}

				if (option.equals("music")) {
					if (action.equals("limit")) {
						boolean isNumber = args[2].matches("^[0-9]*$");
						if (!isNumber) {
							event.getChannel().sendMessage(EmoteReference.ERROR + "That's not a valid number.").queue();
							return;
						}

						try {
							guildData.setMusicSongDurationLimit(Long.parseLong(args[2]));
							dbGuild.save();
							event.getChannel().sendMessage(String.format(EmoteReference.MEGA + "Song duration limit (on ms) on this server is now: %sms.", args[2])).queue();
							return;
						} catch (NumberFormatException e) {
							event.getChannel().sendMessage(EmoteReference.WARNING + "You're trying to set a big af number, silly").queue();
						}
						return;
					}

					if (action.equals("queuelimit")) {
						boolean isNumber = args[2].matches("^[0-9]*$");
						if (!isNumber) {
							event.getChannel().sendMessage(EmoteReference.ERROR + "That's not a valid number.").queue();
							return;
						}
						try {
							int finalSize = Integer.parseInt(args[2]);
							int applySize = finalSize >= 300 ? 300 : finalSize;
							guildData.setMusicQueueSizeLimit((long) applySize);
							dbGuild.save();
							event.getChannel().sendMessage(String.format(EmoteReference.MEGA + "Queue limit on this server is now **%d** songs.", applySize)).queue();
							return;
						} catch (NumberFormatException e) {
							event.getChannel().sendMessage(EmoteReference.ERROR + "You're trying to set a big af number (which won't be applied anyway), silly").queue();
						}
						return;
					}

					if (action.equals("channel")) {
						if (args.length < 3) {
							onHelp(event);
							return;
						}

						String channelName = splitArgs(content)[2];

						VoiceChannel channel = event.getGuild().getVoiceChannelById(channelName);

						if (channel == null) {
							try {
								List<VoiceChannel> voiceChannels = event.getGuild().getVoiceChannels().stream()
									.filter(voiceChannel -> voiceChannel.getName().contains(channelName))
									.collect(Collectors.toList());

								if (voiceChannels.size() == 0) {
									event.getChannel().sendMessage(EmoteReference.ERROR + "I couldn't found any Voice Channel with that Name or Id").queue();
									return;
								} else if (voiceChannels.size() == 1) {
									channel = voiceChannels.get(0);
									guildData.setMusicChannel(channel.getId());
									dbGuild.save();
									event.getChannel().sendMessage(EmoteReference.OK + "Music Channel set to: " + channel.getName()).queue();
								} else {
									DiscordUtils.selectList(event, voiceChannels,
										voiceChannel -> String.format("%s (ID: %s)", voiceChannel.getName(), voiceChannel.getId()),
										s -> baseEmbed(event, "Select the Channel:").setDescription(s).build(),
										voiceChannel -> {
											guildData.setMusicChannel(voiceChannel.getId());
											dbGuild.save();
											event.getChannel().sendMessage(EmoteReference.OK + "Music Channel set to: " + voiceChannel.getName()).queue();
										}
									);
								}
							} catch (Exception e) {
								LOGGER.warn("Error while setting voice channel", e);
								event.getChannel().sendMessage("There has been an error while trying to set the voice channel, maybe try again? " +
									"-> " + e.getClass().getSimpleName()).queue();
							}
						}

						return;
					}

					if (action.equals("clear")) {
						guildData.setMusicSongDurationLimit(null);
						guildData.setMusicChannel(null);
						dbGuild.save();
						event.getChannel().sendMessage(EmoteReference.CORRECT + "Now I can play music on all channels!").queue();
						return;
					}

					onHelp(event);
					return;
				}

				if (option.equals("admincustom")) {
					try {
						guildData.setCustomAdminLock(Boolean.parseBoolean(action));
						dbGuild.save();
						String toSend = EmoteReference.CORRECT + (Boolean.parseBoolean(action) ? "``Permission -> Now user command creation is admin only.``" : "``Permission -> Now user command creation can be done by users.``");
						event.getChannel().sendMessage(toSend).queue();
						return;
					} catch (Exception e) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not a boolean value.").queue();
						return;
					}
				}

				if (option.equals("localmoney")) {
					try {
						guildData.setRpgLocalMode(Boolean.parseBoolean(action));
						dbGuild.save();
						String toSend = EmoteReference.CORRECT + (guildData.getRpgLocalMode() ? "``Money -> Now money on this guild is localized.``" : "``Permission -> Now money on this guild is shared with global.``");
						event.getChannel().sendMessage(toSend).queue();
						return;
					} catch (Exception e) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "Not a boolean value.").queue();
						return;
					}
				}

				if (option.equals("autorole")) {
					if (action.equals("set")) {
						String name = content.replace(option + " " + action + " ", "");
						List<Role> roles = event.getGuild().getRolesByName(name, true);
						StringBuilder b = new StringBuilder();

						if (roles.isEmpty()) {
							event.getChannel().sendMessage(EmoteReference.ERROR + "We didn't find any roles with that name").queue();
							return;
						}

						if (roles.size() <= 1) {
							guildData.setGuildAutoRole(roles.get(0).getId());
							event.getMessage().addReaction("\ud83d\udc4c").queue();
							dbGuild.save();
							event.getChannel().sendMessage(EmoteReference.CORRECT + "Autorole now set to role: **" + roles.get(0).getName() + "** (Position: " + roles.get(0).getPosition() + ")").queue();
							return;
						}

						for (int i = 0; i < 5 && i < roles.size(); i++) {
							Role role = roles.get(i);
							if (role != null)
								b.append('[').append(i + 1).append("] ").append(role.getName()).append(" | Position: ").append(role.getPosition()).append("\n");
						}

						event.getChannel().sendMessage(new EmbedBuilder().setTitle("Selection", null).setDescription(b.toString()).build()).queue();

						IntConsumer roleSelector = (c) -> {
							guildData.setGuildAutoRole(roles.get(c - 1).getId());
							event.getMessage().addReaction("\ud83d\udc4c").queue();
							dbGuild.save();
							event.getChannel().sendMessage(EmoteReference.OK + "Autorole now set to role: **" + roles.get(c - 1).getName() + "** (Position: " + roles.get(c - 1).getPosition() + ")").queue();
						};

						DiscordUtils.selectInt(event, roles.size() + 1, roleSelector);
						return;

					} else if (action.equals("unbind")) {
						guildData.setGuildAutoRole(null);
						event.getChannel().sendMessage(EmoteReference.OK + "Autorole resetted.").queue();
					}
				}

				onHelp(event);
			}

			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.ADMIN;
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Bot options")
					.addField("Description", "This command allows you to set different customizable options for your guild instance of the bot.\n" +
						"All values set here are local, that means, they only take effect on your server and not on other " +
						"servers the bot might be on.", false)
					.setDescription("Usage\n" +
						"~>opts logs enable <channel> - Enables logs to the specified channel (use the name).\n" +
						"~>opts logs disable - Disables server-wide logs.\n" +
						"~>opts prefix set <prefix> - Sets a custom prefix for your server.\n" +
						"~>opts prefix clear - Resets your server custom prefix.\n" +
						"~>opts nsfw toggle - Toggles NSFW usage for this channel to allow usage with explicit images in yandere and other commands.\n" +
						"~>opts birthday enable <channel> <role> - Enables birthday monitoring in your server. Arguments such as channel and role don't accept spaces.\n" +
						"~>opts birthday disable - Disables birthday monitoring.\n" +
						"~>opts music limit <ms> - Changes the music lenght limit.\n" +
						"~>opts music queuelimit <number> - Changes the queue song limit (max is 300 regardless).\n" +
						"~>opts autorole set <role> - Sets the new autorole which will be assigned to users on user join.\n" +
						"~>opts autorole unbind - Clears the autorole config.\n" +
						"~>opts resetmoney - Resets local money.\n" +
						"~>opts localmoney <true/false> - Toggles guild local mode (currency and RPG stats only for your guild).\n" +
						"~>opts music channel <channel> - If set, mantaro will connect only to the specified channel. It might be the name or the ID.\n" +
						"~>opts music clear - If set, mantaro will connect to any music channel the user who called the bot is on if nobody did it already.\n" +
						"~>opts admincustom <true/false> - If set to true, custom commands will only be avaliable for admin creation, otherwise everyone can do it. It defaults to false.")
					.build();
			}
		});
	}

	private void prune() {
		super.register("prune", new SimpleCommand() {
			@Override
			protected void call(String[] args, String content, GuildMessageReceivedEvent event) {
				TextChannel channel = event.getChannel();
				if (content.isEmpty()) {
					channel.sendMessage(EmoteReference.ERROR + "You specified no messages to prune.").queue();
					return;
				}

				if (content.startsWith("bot")) {
					channel.getHistory().retrievePast(100).queue(
						messageHistory -> {
							String prefix = MantaroData.db().getGuild(event.getGuild()).getData().getGuildCustomPrefix();
							messageHistory = messageHistory.stream().filter(message -> message.getAuthor().isBot() ||
								message.getContent().startsWith(prefix == null ? "~>" : prefix)).collect(Collectors.toList());

							if (messageHistory.isEmpty()) {
								event.getChannel().sendMessage(EmoteReference.ERROR + "There are no messages from bots or bot calls here.").queue();
								return;
							}

							final int size = messageHistory.size();

							channel.deleteMessages(messageHistory).queue(
								success -> channel.sendMessage(EmoteReference.PENCIL + "Successfully pruned " + size + " bot messages").queue(),
								error -> {
									if (error instanceof PermissionException) {
										PermissionException pe = (PermissionException) error;
										channel.sendMessage(EmoteReference.ERROR + "Lack of permission while pruning messages" +
											"(No permission provided: " + pe.getPermission() + ")").queue();
									} else {
										channel.sendMessage(EmoteReference.ERROR + "Unknown error while pruning messages" + "<"
											+ error.getClass().getSimpleName() + ">: " + error.getMessage()).queue();
										error.printStackTrace();
									}
								});

						},
						error -> {
							channel.sendMessage(EmoteReference.ERROR + "Unknown error while retrieving the history to prune the messages" + "<"
								+ error.getClass().getSimpleName() + ">: " + error.getMessage()).queue();
							error.printStackTrace();
						}
					);
					return;
				}
				int i = Integer.parseInt(content);

				if (i <= 5) {
					event.getChannel().sendMessage(EmoteReference.ERROR + "You need to provide at least 5 messages.").queue();
					return;
				}

				channel.getHistory().retrievePast(Math.min(i, 100)).queue(
					messageHistory -> {
						messageHistory = messageHistory.stream().filter(message -> !message.getCreationTime()
							.isBefore(OffsetDateTime.now().minusWeeks(2)))
							.collect(Collectors.toList());

						if (messageHistory.isEmpty()) {
							event.getChannel().sendMessage(EmoteReference.ERROR + "There are no messages newer than 2 weeks old, discord won't let me delete them.").queue();
							return;
						}

						final int size = messageHistory.size();

						channel.deleteMessages(messageHistory).queue(
							success -> channel.sendMessage(EmoteReference.PENCIL + "Successfully pruned " + size + " messages").queue(),
							error -> {
								if (error instanceof PermissionException) {
									PermissionException pe = (PermissionException) error;
									channel.sendMessage(EmoteReference.ERROR + "Lack of permission while pruning messages" +
										"(No permission provided: " + pe.getPermission() + ")").queue();
								} else {
									channel.sendMessage(EmoteReference.ERROR + "Unknown error while pruning messages" + "<"
										+ error.getClass().getSimpleName() + ">: " + error.getMessage()).queue();
									error.printStackTrace();
								}
							});
					},
					error -> {
						channel.sendMessage(EmoteReference.ERROR + "Unknown error while retrieving the history to prune the messages" + "<"
							+ error.getClass().getSimpleName() + ">: " + error.getMessage()).queue();
						error.printStackTrace();
					}
				);
			}

			@Override
			public CommandPermission permissionRequired() {
				return CommandPermission.ADMIN;
			}

			@Override
			public MessageEmbed help(GuildMessageReceivedEvent event) {
				return helpEmbed(event, "Prune command")
					.setDescription("Prunes a specific amount of messages.")
					.addField("Usage", "~>prune <x> - Prunes messages", false)
					.addField("Parameters", "x = number of messages to delete", false)
					.addField("Important", "You need to provide at least 3 messages. I'd say better 10 or more.\nYou can use ~>prune bot to remove all bot messages and bot calls.", false)
					.build();
			}
		});
	}

	public static Iterable<String> iterate(Matcher matcher) {
		return new Iterable<String>() {
			@Override
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					@Override
					public boolean hasNext() {
						return matcher.find();
					}

					@Override
					public String next() {
						return matcher.group();
					}
				};
			}

			@Override
			public void forEach(Consumer<? super String> action) {
				while (matcher.find()) {
					action.accept(matcher.group());
				}
			}
		};
	}

	private static long parse(String s) {
		s = s.toLowerCase();
		long[] time = {0};
		iterate(pattern.matcher(s)).forEach(string -> {
			String l = string.substring(0, string.length() - 1);
			TimeUnit unit;
			switch (string.charAt(string.length() - 1)) {
				case 's':
					unit = TimeUnit.SECONDS;
					break;
				case 'm':
					unit = TimeUnit.MINUTES;
					break;
				case 'h':
					unit = TimeUnit.HOURS;
					break;
				case 'd':
					unit = TimeUnit.DAYS;
					break;
				default:
					unit = TimeUnit.SECONDS;
					break;
			}
			time[0] += unit.toMillis(Long.parseLong(l));
		});
		return time[0];
	}
}
