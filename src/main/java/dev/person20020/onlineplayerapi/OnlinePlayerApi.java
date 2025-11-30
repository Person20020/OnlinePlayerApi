package dev.person20020.onlineplayerapi;

import com.earth2me.essentials.Essentials;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.bukkit.Bukkit.getLogger;

public final class OnlinePlayerApi extends JavaPlugin {

    private Chat chat = null;
    private HttpServer server = null;

    private Essentials ess = null;


    @Override
    public void onEnable() {
        saveDefaultConfig();

        File dataFolder = getDataFolder();
        File apiUsers = new File(dataFolder, "ApiUsers.json");
        Path filePath = dataFolder.toPath().resolve("ApiUsers.json");
        if (!apiUsers.exists()) {
            try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
                JSONArray users = new JSONArray();
                JSONObject apiUSersJson = new JSONObject();
                apiUSersJson.put("users", users);
                fileWriter.write(apiUSersJson.toString());
            }
            catch (IOException e) {
                getLogger().warning("Failed to write to users file.");
            }
        }

        if (!setupChat()) {
            getLogger().severe("Vault chat not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("onlineplayerapi").setExecutor(new OnlinePlayerApiCommand(this));

        int serverStartAttemptCount = 0;
        int serverStartAttemptMaxCount = 3;
        while (serverStartAttemptCount < serverStartAttemptMaxCount) { // Try to start 3 times
            startHttpServer();
            if (server != null) {
                break;
            }

            serverStartAttemptCount ++;
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            }
            catch (InterruptedException e) {
                getLogger().warning("Error: " + e.getMessage());
            }
            if (serverStartAttemptCount >= serverStartAttemptMaxCount) {
                getLogger().severe("Failed to start HTTP server. Disabling plugin.");
            }
        }

        // Check if Essentials exists
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");

        if (plugin != null && plugin instanceof Essentials) {
            ess = (Essentials) plugin;
            getLogger().info("Essentials detected, AFK check enabled.");
        }
        else {
            getLogger().info("Essentials not detected, AFK check disabled.");
        }

        getLogger().info("OnlinePlayerApi has started.");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop(0);
        }
        System.out.println("OnlinePlayerApi has stopped.");
    }

    private boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp == null) return false;
        chat = rsp.getProvider();
        return chat != null;
    }

    private void startHttpServer() {
        int port = getConfig().getInt("port", 8080);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", httpExchange -> {
                String response = "OnlinePlayerApi running on port " + port;
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                getLogger().info("Received request at path '/'");
            });
            server.createContext("/api/players", httpExchange -> {
                // Check auth
                Headers headers = httpExchange.getRequestHeaders();
                String response = "";
                int httpResponseCode;
                if (!checkAuth(headers)) { // Auth fails
                    response = "{'ok': false, 'error': 'Incorrect or missing token'}";
                    httpResponseCode = 401;
                }
                else { // Auth succeeds
                    response = getPlayersJson();
                    httpResponseCode = 200;
                }

                httpExchange.sendResponseHeaders(httpResponseCode, response.getBytes().length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                getLogger().info("Received request at path '/api/players' and responded with: " + response);
            });

            server.setExecutor(null);
            server.start();

            getLogger().info("HTTP server running on port " + port);
        }
        catch (IOException e) {
            getLogger().info("Failed to start HTTP server: " + e.getMessage());
        }
    }

    private String getPlayersJson() {
        JSONArray players = new JSONArray();

        for (Player p : Bukkit.getOnlinePlayers()) {
            JSONObject player = new JSONObject();
            player.put("name", p.getName());

            // Prefix
            String prefix = "";
            try {
                prefix = chat.getPlayerPrefix(p);
            }
            catch (Exception e) {
                getLogger().warning("Could not get player prefix: " + e);
            }
            player.put("prefix", prefix == null ? "" : prefix);

            // Groups
            JSONArray groups = new JSONArray();
            try {
                for (String group : chat.getPlayerGroups(p)) {
                    groups.put(group);
                }
            }
            catch (Exception e) {
                getLogger().warning("Could not get player groups: " + e);
            }
            player.put("groups", groups);

            String primaryGroup = "";
            try {
                primaryGroup = chat.getPrimaryGroup(p);
            }
            catch (Exception e) {
                getLogger().warning("Could not get player primary group: " + e);
            }
            player.put("primary_group", primaryGroup == null ? "" : primaryGroup);

            // AFK status
            if (ess != null) {
                boolean afk = ess.getUser(p).isAfk();
                player.put("is_afk", afk);
            }

            players.put(player);
        }

        JSONObject root = new JSONObject();
        root.put("players", players);

        int playerCount = players.length();
        root.put("player_count", playerCount);

        return root.toString();
    }

    private boolean checkAuth(Headers headers) {
        if (headers == null) { return false; }
        // Get api key
        String apiKey = headers.getFirst("X-API-KEY");
        if (apiKey == null) { return false; }
        String apiKeyPrefix = apiKey.split("\\.")[0];

        // Check if pw hash == stored hash
        File dataFolder = getDataFolder();
        File apiUsers = new File(dataFolder, "ApiUsers.json");
        if (!apiUsers.exists()) { getLogger().warning("ApiUsers.json not found."); return false; }

        String content = "";
        try {
            content = Files.readString(apiUsers.toPath());
        }
        catch (IOException e) {
            getLogger().warning("Failed to read ApiUsers.json file.");
            return false;
        }
        JSONObject apiUsersJson = new JSONObject(content);

        JSONArray users = apiUsersJson.getJSONArray("users");

        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            // Find api key based on prefix and then return
            if (user.getString("prefix").equals(apiKeyPrefix)) {
                return BCrypt.checkpw(apiKey, user.getString("api_key_hash"));
            }
        }

        return false;
    }
}

