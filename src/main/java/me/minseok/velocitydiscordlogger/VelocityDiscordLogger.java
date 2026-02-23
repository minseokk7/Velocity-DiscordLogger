package me.minseok.velocitydiscordlogger;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(id = "velocity-discordlogger", name = "Velocity-DiscordLogger", version = "1.0.2", description = "Network-level Discord logging with embeds and player icons", authors = {
        "minseok" })
public class VelocityDiscordLogger {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private JDA jda;
    private DiscordMessageBuilder messageBuilder;
    private DiscordConsoleAppender consoleAppender;
    private Database database;
    private LinkCommand linkCommand;

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private static VelocityDiscordLogger instance;

    @Inject
    public VelocityDiscordLogger(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    public static VelocityDiscordLogger getInstance() {
        return instance;
    }

    public JDA getJda() {
        return jda;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("VelocityDiscordLogger starting...");

        // Load configuration
        try {
            loadConfig();
        } catch (IOException e) {
            logger.error("Failed to load configuration!", e);
            return;
        }

        // Initialize Discord bot asynchronously to prevent proxy freeze
        server.getScheduler().buildTask(this, () -> {
            try {
                jda = JDABuilder.createDefault(config.getBotToken())
                        .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT,
                                GatewayIntent.DIRECT_MESSAGES)
                        .build();

                // Wait for JDA to be fully connected (blocking only the async task thread)
                jda.awaitReady();

                logger.info("Discord bot connected successfully!");

                // Validate channel IDs
                if (!validateChannelIds()) {
                    logger.warn("Some Discord channel IDs are invalid - check your configuration!");
                }

                // Initialize message builder
                messageBuilder = new DiscordMessageBuilder(config);

                // Initialize Database
                database = new Database(config, logger);
                try {
                    database.connect();
                    logger.info("Connected to database!");
                } catch (Exception e) {
                    logger.error("Failed to connect to database", e);
                }

                // Register plugin messaging channel
                server.getChannelRegistrar().register(PluginMessageListener.getIdentifier());
                PluginMessageListener pluginMessageListener = new PluginMessageListener(jda, config, messageBuilder,
                        logger);
                server.getEventManager().register(VelocityDiscordLogger.this, pluginMessageListener);

                // Register event listener for join/quit
                server.getEventManager().register(VelocityDiscordLogger.this,
                        new PlayerEventListener(jda, config, messageBuilder, logger));

                // Register Chat Listener
                server.getEventManager().register(VelocityDiscordLogger.this,
                        new ChatListener(jda, config, logger, database, server, VelocityDiscordLogger.this));

                // Register Link Command
                linkCommand = new LinkCommand();
                server.getCommandManager().register(
                        server.getCommandManager().metaBuilder("link").build(),
                        linkCommand);

                // Schedule LinkCommand cleanup task (every 1 minute)
                server.getScheduler().buildTask(VelocityDiscordLogger.this, () -> linkCommand.cleanup())
                        .repeat(java.time.Duration.ofMinutes(1))
                        .schedule();

                // Register Slash Command Listener
                jda.addEventListener(new SlashCommandListener(server, config, logger, database, linkCommand));

                // Register Discord Event Listener (Chat)
                jda.addEventListener(new DiscordEventListener(server, config, logger, database, linkCommand));

                // Register Discord Command
                server.getCommandManager().register(
                        server.getCommandManager().metaBuilder("discord").build(),
                        new DiscordCommand(config, database));

                // Register Slash Commands
                jda.updateCommands().addCommands(
                        net.dv8tion.jda.api.interactions.commands.build.Commands.slash("cmd", "Execute a proxy command")
                                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "command",
                                        "The command to execute", true),
                        net.dv8tion.jda.api.interactions.commands.build.Commands.slash("status",
                                "Show proxy server status"),
                        net.dv8tion.jda.api.interactions.commands.build.Commands
                                .slash("link", "Link your Minecraft account")
                                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "code",
                                        "The linking code from in-game", true))
                        .queue();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Discord bot initialization interrupted!", e);
            } catch (Exception e) {
                logger.error("Failed to connect to Discord!", e);
            }
        }).schedule();

        // Setup Console Logging
        setupConsoleLogging();

        // Send Server Start Message
        if (config.isStartEnabled()) {
            String channelId = config.getStatusChannelId();
            logger.info("Attempting to send start message to channel: " + channelId);

            if (!channelId.isEmpty()) {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    try {
                        @SuppressWarnings("null")
                        var action = channel.sendMessageEmbeds(messageBuilder.buildServerStartEmbed());
                        action.complete();
                        logger.info("Server start message sent successfully!");
                    } catch (Exception e) {
                        logger.error("Failed to send server start message", e);
                    }
                } else {
                    logger.warn("Status channel NOT found! ID: " + channelId);
                }
            } else {
                logger.warn("Status channel ID is empty in config!");
            }
        }

        // Register JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM Shutdown Hook triggered!");
            handleShutdown();
        }));

        logger.info("VelocityDiscordLogger started successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("ProxyShutdownEvent received.");
        handleShutdown();
    }

    private void handleShutdown() {
        if (isShuttingDown.getAndSet(true)) {
            return;
        }

        logger.info("Handling shutdown sequence...");

        // Send Server Stop Message via REST API (More reliable during shutdown)
        if (config != null && config.isStopEnabled()) {
            String channelId = config.getStatusChannelId();
            if (!channelId.isEmpty()) {
                sendShutdownMessageViaRest(channelId);
            }
        }

        // Artificial delay to ensure network buffers flush and logs are written
        try {
            logger.info("Delaying shutdown by 3 seconds to ensure message delivery...");
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            // 1. Console appender 종료
            if (consoleAppender != null) {
                consoleAppender.stop();
                org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
                        .getRootLogger();
                coreLogger.removeAppender(consoleAppender);
                logger.info("Console appender stopped");
            }

            // 2. JDA 종료 (대기 포함)
            if (jda != null) {
                logger.info("Shutting down JDA...");
                jda.shutdownNow();
                if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                    logger.warn("JDA shutdown timeout!");
                }
                logger.info("JDA shutdown complete");
            }

            // 3. 데이터베이스 정리
            if (database != null) {
                database.close();
                logger.info("Database connection closed");
            }

        } catch (Exception e) {
            logger.error("Error during shutdown cleanup", e);
        }

        logger.info("VelocityDiscordLogger stopped.");
    }

    private void sendShutdownMessageViaRest(String channelId) {
        try {
            java.net.URL url = new java.net.URL("https://discord.com/api/v10/channels/" + channelId + "/messages");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bot " + config.getBotToken());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "VelocityDiscordLogger/1.0");
            conn.setDoOutput(true);

            // Construct simple JSON payload for embed
            // Color #ff0000 is 16711680
            String jsonBody = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \":octagonal_sign: 서버가 종료되었습니다.\","
                    + "\"color\": 16711680"
                    + "}]"
                    + "}";

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            logger.info("Sent shutdown message via REST. Response Code: " + responseCode);

            // Read response to ensure it completes
            try (java.io.InputStream is = (responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream())) {
                if (is != null)
                    is.readAllBytes();
            }

        } catch (Exception e) {
            logger.error("Failed to send shutdown message via REST", e);
        }
    }

    private void setupConsoleLogging() {
        if (config.getConsoleChannelId().isEmpty())
            return;

        try {
            org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
                    .getRootLogger();
            consoleAppender = new DiscordConsoleAppender(jda, config);
            consoleAppender.startLogSender();
            coreLogger.addAppender(consoleAppender);
            logger.info("Console logging enabled for channel: " + config.getConsoleChannelId());
        } catch (Exception e) {
            logger.error("Failed to setup console logging", e);
        }
    }

    /**
     * Discord 채널 ID들을 검증합니다
     * 
     * @return 모든 채널이 유효하면 true
     */
    private boolean validateChannelIds() {
        boolean allValid = true;
        java.util.List<String> channelNames = java.util.Arrays.asList(
                "log", "chat", "console", "status");

        for (String channelName : channelNames) {
            String channelId;
            switch (channelName) {
                case "log":
                    channelId = config.getLogChannelId();
                    break;
                case "chat":
                    channelId = config.getChatChannelId();
                    break;
                case "console":
                    channelId = config.getConsoleChannelId();
                    break;
                case "status":
                    channelId = config.getStatusChannelId();
                    break;
                default:
                    continue;
            }

            if (channelId == null || channelId.isEmpty()) {
                logger.warn("Channel ID for '" + channelName + "' is not configured");
                continue;
            }

            try {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(channelId);
                if (channel == null) {
                    logger.warn("Channel ID '" + channelId + "' for '" + channelName + "' not found!");
                    allValid = false;
                } else {
                    logger.info("✓ Channel '" + channelName + "' validated: " + channel.getName());
                }
            } catch (Exception e) {
                logger.warn("Error validating channel '" + channelName + "': " + e.getMessage());
                allValid = false;
            }
        }

        return allValid;
    }

    private void loadConfig() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        File configFile = dataDirectory.resolve("config.toml").toFile();

        if (!configFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.toml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    logger.info("Default configuration created. Please configure bot token and channel IDs.");
                }
            }
        }

        config = new PluginConfig(configFile.toPath());
    }
}
