package wtf.blu.simpleQueue;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import wtf.blu.simpleQueue.util.WtfYamlConfiguration;
import wtf.blu.simpleQueue.util.DiscordWebhookUtil;

import java.util.logging.Logger;

class OnPlayerLoginListener implements Listener {
    private final Server server;
    private final Logger logger;
    private final PlayerQueue playerQueue;
    private final WtfYamlConfiguration configYaml;

    private final int maxPlayerCount;

    /* Config */
    private final String kickMessage;
    private final long mustReconnectWithinSec;
    private final int reservedSlots;
    private final DiscordWebhookUtil discordWebhook;

    OnPlayerLoginListener(Server server, Logger logger, PlayerQueue playerQueue, WtfYamlConfiguration configYaml) {
        this.server = server;
        this.logger = logger;
        this.playerQueue = playerQueue;
        this.configYaml = configYaml;

        maxPlayerCount = server.getMaxPlayers();

        kickMessage = configYaml.getString("kickMessageQueued");
        mustReconnectWithinSec = configYaml.getLong("mustReconnectWithinSec");
        reservedSlots = configYaml.getInt("reservedSlots");
        
        // Initialize Discord webhook if enabled
        boolean webhookEnabled = configYaml.getBoolean("discordWebhook.enabled", false);
        String webhookUrl = configYaml.getString("discordWebhook.webhookUrl", "");
        Plugin plugin = server.getPluginManager().getPlugin("SimpleQueue");
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
            this.discordWebhook = new DiscordWebhookUtil((org.bukkit.plugin.java.JavaPlugin) plugin, webhookUrl, webhookEnabled);
        } else {
            this.discordWebhook = new DiscordWebhookUtil(null, webhookUrl, false);
            logger.warning("[SimpleQueue] Failed to initialize Discord webhook: Could not get JavaPlugin instance");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onPlayerLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();

        if(e.getResult() != PlayerLoginEvent.Result.ALLOWED && e.getResult() != PlayerLoginEvent.Result.KICK_FULL) {
            logger.warning("[SimpleQueue]: A player logged in but had an unexpected login result. Please report this if you this is an error.");
            playerQueue.removePlayer(player);
            return;
        }

        int currentPlayerCount = server.getOnlinePlayers().size();

        //Checks if the player is able to ignore the slot limit
        if(SimpleQueuePermission.IGNORE_SLOT_LIMIT.hasPermission(player)) {
            playerQueue.removePlayer(player);
            e.allow();
            return;
        }

        int indexInQueue = playerQueue.getCurrentIndexOrInsert(player);

        if (
                //Is first in queue
                (indexInQueue == 0) && (
                    //server is not full(incl reserved slots)
                    (maxPlayerCount > (currentPlayerCount + reservedSlots)) ||
                    //server is not full and if prioritized player
                    ((maxPlayerCount > currentPlayerCount) && SimpleQueuePermission.PRIORITIZED_PLAYER.hasPermission(player))
                )) {
            playerQueue.removePlayer(player);
            //Intentionally no e.allow();
            return;
        }

        //Still in queue
        sendDiscordWebhook(player, indexInQueue);
        kickedQueued(e, indexInQueue);
    }

    private void sendDiscordWebhook(Player player, int index) {
        if (!discordWebhook.isEnabled()) return;
        
        int totalInQueue = playerQueue.getQueuedPlayers().size();
        int estimatedWaitMins = calculateWaitTimeInMinutes(index);
        
        // Get the message template
        String message = configYaml.getString("discordWebhook.message", 
                ":hourglass: **%player%** has joined the queue (Position: %position%/%total%, Estimated wait: %estimate% min)");
        
        // Replace placeholders with actual values
        message = message
                .replace("%player%", player.getName())
                .replace("%position%", String.valueOf(index + 1))
                .replace("%total%", String.valueOf(totalInQueue))
                .replace("%estimate%", String.valueOf(estimatedWaitMins));
                
        discordWebhook.sendMessage(message);
    }
    
    private int calculateWaitTimeInMinutes(int index) {
        // Calculate estimated wait time in minutes (assuming 30 seconds per player in front)
        int estimatedWaitMins = (int) Math.ceil((index * 30) / 60.0);
        // Ensure at least 1 minute if there's any wait time
        return Math.max(1, estimatedWaitMins);
    }
    
    private void kickedQueued(PlayerLoginEvent e, int index) {
        e.setResult(PlayerLoginEvent.Result.KICK_FULL);

        int estimatedWaitMins = calculateWaitTimeInMinutes(index);

        // Replace placeholders with actual values
        String formattedMessage = kickMessage
                .replace("%position%", String.valueOf(index + 1))
                .replace("%total%", String.valueOf(playerQueue.getQueuedPlayers().size()))
                .replace("%wait%", String.valueOf(estimatedWaitMins))
                .replace("%reconnect%", String.valueOf(mustReconnectWithinSec))
                // Escape any remaining % signs to prevent format string errors
                .replace("%", "%%");
        
        // Apply color codes
        String kickMessageFormatted = ChatColor.translateAlternateColorCodes('&', formattedMessage);

        e.disallow(e.getResult(), kickMessageFormatted);
    }
}
