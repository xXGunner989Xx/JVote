package com.wkaye.jvote;

import com.johnymuffin.beta.fundamentals.util.TimeTickConverter;
import com.johnymuffin.beta.fundamentals.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class JVoteCommand implements CommandExecutor {
    private final JVote plugin;
    // need thread safety for this variable so that two players cant start a vote at the same time
    private final AtomicBoolean voteStarted;
    private final AtomicBoolean isVoteTimePassed;
    private final AtomicInteger totalVotes;
    private final HashSet<Player> playerHasVoted;
    // this will be the world where the vote is initiated
    private JVoteEnums currentVoteType;
    private World world;

    public JVoteCommand(JVote plugin) {
        this.plugin = plugin;
        voteStarted = new AtomicBoolean(false);
        totalVotes = new AtomicInteger(0);
        isVoteTimePassed = new AtomicBoolean(false);
        playerHasVoted = new HashSet<>();
    }


    /*
     This command should have two stages: one where the voting commences and another where people vote yes/no
     Requirements:
         Starting a vote automatically votes yes for that player
         A player can only vote once
         If a cutoff is passed (let's say 50% of online players), then vote should pass
         Either one no vote will kill a vote or it should subtract one (-1) from total vote count
         End vote after a timer (1 minute will be baseline)
      */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.logger(Level.INFO, Arrays.toString(args));
        plugin.logger(Level.INFO, String.valueOf(args.length));
        // there should be two types of votes: one where the vote is initiated, and one where the vote is yes/no
        if (args.length != 1) {
            // invalid usage (should be /vote {type of vote}
            plugin.logger(Level.WARNING, "Attempted /vote with invalid number of args");
            return true;
        }
        if (!(sender instanceof Player)) {
            // command not available to console message
            plugin.logger(Level.WARNING, "Attempted /vote from console");
            return true;
        }
        Player player = (Player) sender;
        if (voteStarted.get()) {
            // vote started, check that the user actually supplied a yes or no vote
            if (!(args[0].equalsIgnoreCase("yes") || args[0].equalsIgnoreCase("no"))) {
                // invalid usage, return false
                plugin.logger(Level.WARNING, "Attempted /vote after vote started with improper args");
                return true;
            }
            if (checkAndDoVote(args[0], player)) {
                // vote passed, perform change and reset values

                resetValues();
                return true;
            }

        }
        if (!voteStarted.get()) {
            // TODO: fetch these from some config file. For now, only day/night and clear/storm
            // invalid usage, return false
            try {
                currentVoteType = JVoteEnums.valueOf(args[0].toUpperCase());
                String sb = Utils.formatColor("&7[&bJVote&7] A vote for ") +
                        Utils.formatColor(currentVoteType.color()) +
                        Utils.formatColor(currentVoteType.toString().toLowerCase()) +
                        Utils.formatColor("&7 has started");
                plugin.getServer().broadcastMessage(sb);
                world = player.getWorld();
                if (checkAndDoVote("yes", player)) {
                    resetValues();
                }

            } catch (IllegalArgumentException e) {
                plugin.logger(Level.WARNING, "Attempted to start /vote with improper argument");
                return true;
            }
        }


        return true;
    }

    private boolean checkAndDoVote(String arg, Player player) {
        double currentVotePercentage;
        if (playerHasVoted.contains(player)) {
            // send message to player that he/she already voted and return
            String msg = "&7[&bJVote&7] You have already voted.";
            player.sendMessage(msg);
            return false;
        }
        if (arg.equalsIgnoreCase("yes")) {
            currentVotePercentage = (double) totalVotes.incrementAndGet()
                    / Bukkit.getServer().getOnlinePlayers().length;
            if (currentVotePercentage < 0.5) {
                return false;
            }
        } else {
            totalVotes.decrementAndGet();
            return false;
        }
        // vote passed, do stuff
        if (currentVoteType == null) {
            plugin.logger(Level.SEVERE, "Unexpected error when getting vote type");
            return false;
        } else {
            switch (currentVoteType) {
                case DAY:
                    long ticks = TimeTickConverter.hoursMinutesToTicks(6, 0);
                    world.setTime(ticks);
                    break;
                default:
                    plugin.logger(Level.WARNING, "Not implemented yet");
            }
            player.sendMessage("&7[&bJVote&7] You have voted.");
            playerHasVoted.add(player);
            return true;
        }
    }

    private void resetValues() {
        voteStarted.set(false);
        totalVotes.set(0);
        isVoteTimePassed.set(false);
        currentVoteType = null;
        playerHasVoted.clear();
    }

    // this function will handle the timer and check that the vote has passed.
    private void doVoteTimer(JVoteEnums cmd) {

    }
}
