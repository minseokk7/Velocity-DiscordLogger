package me.minseok.velocitydiscordlogger;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordCommand implements SimpleCommand {

    private final PluginConfig config;
    private final Database database;

    public DiscordCommand(PluginConfig config, Database database) {
        this.config = config;
        this.database = database;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(Component.text("사용법: /discord <reload|unlink|debug>", NamedTextColor.RED));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                // Permission check removed for testing
                // if (!source.hasPermission("velocitydiscordlogger.reload")) {
                // source.sendMessage(
                // Component.text("You do not have permission to use this command.",
                // NamedTextColor.RED));
                // return;
                // }
                try {
                    config.reload();
                    source.sendMessage(Component.text("설정을 다시 불러왔습니다!", NamedTextColor.GREEN));
                } catch (Exception e) {
                    source.sendMessage(
                            Component.text("설정 리로드 실패: " + e.getMessage(), NamedTextColor.RED));
                    e.printStackTrace();
                }
                break;

            case "unlink":
                if (!(source instanceof Player)) {
                    source.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
                    return;
                }
                Player player = (Player) source;
                UUID uuid = player.getUniqueId();

                CompletableFuture.runAsync(() -> {
                    database.unlinkAccount(uuid);
                    source.sendMessage(Component.text("디스코드 연동이 해제되었습니다.", NamedTextColor.GREEN));
                });
                break;

            case "debug":
                if (!(source instanceof Player)) {
                    source.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
                    return;
                }
                if (!source.hasPermission("velocitydiscordlogger.debug")) {
                    source.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return;
                }
                Player p = (Player) source;
                source.sendMessage(Component.text("=== VelocityDiscordLogger 디버그 ===", NamedTextColor.GOLD));

                CompletableFuture.runAsync(() -> {
                    // 1. DB Check
                    String discordId = database.getDiscordId(p.getUniqueId());
                    source.sendMessage(
                            Component.text("연동된 Discord ID: " + (discordId == null ? "연동 안됨" : discordId),
                                    discordId == null ? NamedTextColor.RED : NamedTextColor.GREEN));

                    // 2. Config Check
                    String chatChannelId = config.getChatChannelId();
                    source.sendMessage(
                            Component.text("채팅 채널 ID: " + (chatChannelId.isEmpty() ? "미설정" : chatChannelId),
                                    chatChannelId.isEmpty() ? NamedTextColor.RED : NamedTextColor.GREEN));

                    // 3. JDA Check
                    if (discordId != null && !chatChannelId.isEmpty()) {
                        try {
                            net.dv8tion.jda.api.JDA jda = me.minseok.velocitydiscordlogger.VelocityDiscordLogger
                                    .getInstance().getJda();
                            if (jda != null) {
                                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda
                                        .getTextChannelById(chatChannelId);
                                if (channel != null) {
                                    source.sendMessage(Component.text("채팅 채널 발견: " + channel.getName(),
                                            NamedTextColor.GREEN));
                                    net.dv8tion.jda.api.entities.Guild guild = channel.getGuild();
                                    net.dv8tion.jda.api.entities.Member member = guild.retrieveMemberById(discordId)
                                            .complete();
                                    if (member != null) {
                                        source.sendMessage(
                                                Component.text("서버 멤버 발견: " + member.getEffectiveName(),
                                                        NamedTextColor.GREEN));
                                        source.sendMessage(Component.text(
                                                "아바타 URL: " + member.getEffectiveAvatarUrl(), NamedTextColor.GREEN));
                                    } else {
                                        source.sendMessage(
                                                Component.text("서버 멤버를 찾을 수 없습니다!", NamedTextColor.RED));
                                    }
                                } else {
                                    source.sendMessage(
                                            Component.text("JDA에서 채팅 채널을 찾을 수 없습니다!", NamedTextColor.RED));
                                }
                            } else {
                                source.sendMessage(Component.text("JDA 인스턴스가 null입니다!", NamedTextColor.RED));
                            }
                        } catch (Exception e) {
                            source.sendMessage(
                                    Component.text("JDA 확인 중 오류 발생: " + e.getMessage(), NamedTextColor.RED));
                            e.printStackTrace();
                        }
                    }
                });
                break;

            default:
                source.sendMessage(
                        Component.text("알 수 없는 서브명령어입니다. 사용법: /discord <reload|unlink|debug>",
                                NamedTextColor.RED));
                break;
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || args.length == 1) {
            return List.of("reload", "unlink", "debug");
        }
        return List.of();
    }
}
