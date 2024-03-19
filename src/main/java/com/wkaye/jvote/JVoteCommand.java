package com.wkaye.jvote;

import com.johnymuffin.beta.fundamentals.util.TimeTickConverter;
import com.johnymuffin.beta.fundamentals.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

// TODO: implement 1-day cooldown
// TODO: implement string config and permissions config
public class JVoteCommand implements CommandExecutor {
    private final JVote plugin;
    // need thread safety for this variable so that two players cant start a vote at the same time
    AtomicBoolean voteStarted;
    AtomicBoolean isVoteTimePassed;
    AtomicInteger totalVotes;
    HashSet<Player> playerHasVoted;
    // this will be the world where the vote is initiated
    JVoteEnums currentVoteType;
    private World world;

    public JVoteCommand(JVote plugin) {
        System.out.println("plugin instance created");
        this.plugin = plugin;
        voteStarted = new AtomicBoolean(false);
        isVoteTimePassed = new AtomicBoolean(false);
        totalVotes = new AtomicInteger(0);
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
                player.sendMessage("[JVote] A vote is already in progress.");
                plugin.logger(Level.WARNING, "Attempted /vote after vote started with improper args");
                return true;
            }
            if (checkVote(args[0], player)) {
                // vote passed, perform change and reset values
                doVote();
                return true;
            }

        }
        if (!voteStarted.get()) {
            // TODO: fetch these from some config file. For now, only day/night and clear/storm
            // invalid usage, return false
            try {
                currentVoteType = JVoteEnums.valueOf(args[0].toUpperCase());
                String msg = JVoteUtils.printMessage(
                        "A vote for "
                                + Utils.formatColor(currentVoteType.color())
                                + Utils.formatColor(currentVoteType.toString().toLowerCase())
                                + Utils.formatColor("&7 has started"));
                plugin.getServer().broadcastMessage(msg);
                world = player.getWorld();
                if (checkVote("yes", player)) {
                    doVote();
                } else {
                    doVoteTimer(currentVoteType);
                }

            } catch (IllegalArgumentException e) {
                plugin.logger(Level.WARNING, "Attempted to start /vote with improper argument");
                return true;
            }
        }


        return true;
    }

    private boolean checkVote(String arg, Player player) {
        double currentVotePercentage = 0;
        if (playerHasVoted.contains(player)) {
            // send message to player that he/she already voted and return
            String msg = "[JVote] You have already voted.";
            player.sendMessage(msg);
            return false;
        }
        player.sendMessage("[JVote] You have voted.");
        playerHasVoted.add(player);
        if (arg.equalsIgnoreCase("yes")) {
            currentVotePercentage = (double) totalVotes.incrementAndGet()
                    / Bukkit.getServer().getOnlinePlayers().length;
        } else if (arg.equalsIgnoreCase("no")) {
            plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Someone has voted no!"));
            totalVotes.decrementAndGet();
        }
        return currentVotePercentage >= 0.5;
    }

    private boolean checkVote() {
        double currentVotePercentage;
        currentVotePercentage = (double) totalVotes.get()
                / Bukkit.getServer().getOnlinePlayers().length;
        return currentVotePercentage >= 0.5;
    }

    private void doVote() {
        if (currentVoteType == null) {
            plugin.logger(Level.SEVERE, "Unexpected error when getting vote type");
        } else {
            switch (currentVoteType) {
                case DAY:
                    world.setTime(TimeTickConverter.hoursMinutesToTicks(6, 0));
                    break;
                case NIGHT:
                    world.setTime(TimeTickConverter.hoursMinutesToTicks(19, 0));
                case CLEAR:
                    world.setStorm(false);
                case STORMY:
                    world.setStorm(true);
                default:
                    plugin.logger(Level.WARNING, "Not implemented yet");
            }
            plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Vote passed."));
            Bukkit.getScheduler().cancelTasks(plugin);
            resetValues();
        }
    }

    private void resetValues() {
        plugin.logger(Level.INFO, "Resetting values after vote ended");
        voteStarted.set(false);
        totalVotes.set(0);
        isVoteTimePassed.set(false);
        currentVoteType = null;
        playerHasVoted.clear();
    }

    // this function will handle the timer and check that the vote has passed at 1s intervals
    private void doVoteTimer(JVoteEnums cmd) {
        if (!voteStarted.get()) {
            AtomicInteger count = new AtomicInteger(30);
            voteStarted.set(true);
            Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
                int curr = count.getAndDecrement();
                if (curr == 0) {
                    plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Voting has ended."));
                } else {
                    if (curr == 30 || curr == 20 || curr == 10 || curr == 5) {
                        plugin.getServer().broadcastMessage(JVoteUtils.printMessage(curr + " seconds remaining"));
                    }
                }
                if (checkVote()) {
                    doVote();
                    plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Vote passed."));
                    Bukkit.getScheduler().cancelTasks(plugin);
                }
            }, 20, 20);
        } else {
            plugin.logger(Level.INFO, "Vote already in progress");
        }
    }
}
