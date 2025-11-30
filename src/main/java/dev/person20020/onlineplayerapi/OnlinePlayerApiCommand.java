package dev.person20020.onlineplayerapi;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.bukkit.Bukkit.getLogger;

public class OnlinePlayerApiCommand implements CommandExecutor {

    private final OnlinePlayerApi plugin;

    public OnlinePlayerApiCommand(OnlinePlayerApi plugin) {
        this.plugin = plugin;
    }

    // create user command
    // add everything to json file
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage:\n/onlineplayerapi newuser <username>");
            return false;
        }
        if (args[0].equalsIgnoreCase("newuser")) {
            if (args.length != 2) {
                sender.sendMessage("Usage:\n/onlineplayerapi newuser <username>");
                return false;
            }
            String username = args[1];

            // Get data
            File dataFolder = plugin.getDataFolder();
            File apiUsers = new File(dataFolder, "ApiUsers.json");
            if (!apiUsers.exists()) { getLogger().warning("ApiUsers.json not found."); }
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

            // Check if username is used
            for (int i = 0; i < users.length() - 1; i++) {
                JSONObject user = users.getJSONObject(i);
                if (user.getString("username").equalsIgnoreCase(username)) {
                    sender.sendMessage("Username is already in use. Please delete the old user first or choose a new username.");
                    return false;
                }
            }

            // Generate api key
            int apiKeyLength = 32;
            SecureRandom random = new SecureRandom();
            int numApiKeyBytes = (int) Math.ceil(apiKeyLength * 3 / 4.0);
            byte[] apiKeyBytes = new byte[numApiKeyBytes];
            random.nextBytes(apiKeyBytes);
            String newApiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(apiKeyBytes);

            // Generate prefix
            Set<String> existingPrefixes = new HashSet<>();
            for (int i = 0; i < users.length() - 1; i++) {
                JSONObject user = users.getJSONObject(i);
                existingPrefixes.add(user.getString("prefix"));
            }

            String newPrefix;
            do {
                int prefixLength = 4;
                int numPrefixBytes = (int) Math.ceil(prefixLength * 3 / 4.0);
                byte[] prefixBytes = new byte[numPrefixBytes];
                random.nextBytes(prefixBytes);
                newPrefix = Base64.getUrlEncoder().withoutPadding().encodeToString(prefixBytes);
            } while (existingPrefixes.contains(newPrefix));

            newApiKey = newPrefix + "." + newApiKey;
            JSONObject newUser = new JSONObject();
            newUser.put("username", username);
            newUser.put("prefix", newPrefix);
            newUser.put("api_key_hash", BCrypt.hashpw(newApiKey, BCrypt.gensalt(12)));

            users.put(newUser);

            apiUsersJson.clear();
            apiUsersJson.put("users", users);

            Path filePath = dataFolder.toPath().resolve("ApiUsers.json");
            try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
                fileWriter.write(apiUsersJson.toString());
            }
            catch (IOException e) {
                getLogger().warning("Failed to write user to file.");
                sender.sendMessage("Failed to write user to file.");
                return false;
            }

            sender.sendMessage(ChatColor.GREEN + "User '" + ChatColor.WHITE + username + ChatColor.GREEN + "' added. Here is your API key:\n" + ChatColor.WHITE + newApiKey + ChatColor.GREEN + "\nYou can copy it from https://copy.person20020.dev?text=" + newApiKey);
            return true;
        }
        else if (args[0].equalsIgnoreCase("rmuser")) {
            if (args.length != 3 || args[2] != "confirm") {
                sender.sendMessage(ChatColor.RED + "This will delete the api key for this user and is not reversible!\nRun '/onlineplayerapi rmuser <username> confirm' to delete it.");
            }
            else {
                String username = args[1];
                sender.sendMessage(ChatColor.GREEN + "User '" + ChatColor.WHITE + username + ChatColor.GREEN + "' has been deleted.");
            }
            return true;
        }
        else {
            return false;
        }
    }
}