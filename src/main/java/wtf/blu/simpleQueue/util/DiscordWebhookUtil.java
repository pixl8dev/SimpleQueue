package wtf.blu.simpleQueue.util;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhookUtil {
    private final JavaPlugin plugin;
    private final String webhookUrl;
    private final boolean enabled;

    public DiscordWebhookUtil(JavaPlugin plugin, String webhookUrl, boolean enabled) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isEmpty();
    }
    
    public void sendMessage(String message) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    JsonObject json = new JsonObject();
                    json.addProperty("content", message);
                    
                    byte[] out = json.toString().getBytes(StandardCharsets.UTF_8);
                    
                    URL url = new URL(webhookUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("User-Agent", "SimpleQueue/1.0");
                    connection.setRequestProperty("Content-Length", String.valueOf(out.length));
                    
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(out);
                    }
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 200 || responseCode > 299) {
                        plugin.getLogger().warning("Failed to send Discord webhook: HTTP " + responseCode);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}
