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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

// TODO: implement string config and permissions config
public class JVoteCommand implements CommandExecutor {
    private final JVote plugin;
    // need thread safety for this variable so that two players cant start a vote at the same time
    AtomicBoolean voteStarted;
    AtomicBoolean isVoteTimePassed;
    AtomicInteger countdownTaskId;

    // enum: task ID mapping for cancelling task
    ConcurrentHashMap<JVoteEnums, Integer> isOnCooldown;
    AtomicInteger totalVotes;
    HashSet<Player> playerHasVoted;
    JVoteEnums currentVoteType;
    // world where the vote is initiated
    private World world;

    public JVoteCommand(JVote plugin) {
        System.out.println("plugin instance created");
        this.plugin = plugin;
        voteStarted = new AtomicBoolean(false);
        isVoteTimePassed = new AtomicBoolean(false);
        totalVotes = new AtomicInteger(0);
        playerHasVoted = new HashSet<>();
        isOnCooldown = new ConcurrentHashMap<>();
        countdownTaskId = new AtomicInteger();
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
                sender.sendMessage(JVoteUtils.printMessage("vote is already in progress"));
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
            try {
                // this line to trigger exception if not valid
                JVoteEnums.valueOf(args[0].toUpperCase());
                if (isOnCooldown.containsKey(JVoteEnums.valueOf(args[0].toUpperCase()))) {
                    // this vote is on a cool down still
                    sender.sendMessage(JVoteUtils.printMessage("This vote is on cool down"));
                    return true;
                }
                // TODO: fetch these from some config file. For now, only day/night and clear/storm
                // invalid usage, return false
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
                    doVoteTimer();
                }

            } catch (IllegalArgumentException e) {
                plugin.logger(Level.WARNING, "Attempted to start /vote with improper argument");
                return true;
            }
        }


        return true;
    }

    private boolean checkVote(String arg, CommandSender sender) {
        Player player = (Player) sender;
        double currentVotePercentage = 0;
        if (playerHasVoted.contains(player)) {
            // send message to player that he/she already voted and return
            String msg = JVoteUtils.printMessage("You have already voted");
            sender.sendMessage(msg);
            return false;
        }
        sender.sendMessage(JVoteUtils.printMessage("You have voted"));
        playerHasVoted.add(player);
        if (arg.equalsIgnoreCase("yes")) {
            currentVotePercentage = (double) totalVotes.incrementAndGet()
                    / Bukkit.getServer().getOnlinePlayers().length;
        } else if (arg.equalsIgnoreCase("no")) {
            plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Someone has voted no!"));
            Bukkit.getScheduler().cancelTask(countdownTaskId.get());
            resetValues(currentVoteType);
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
                    if (world.hasStorm() || world.isThundering()) {
                        world.setThundering(false);
                        world.setWeatherDuration(20);
                    }
                case STORMY:
                    if (!world.hasStorm() && !world.isThundering()) {
                        world.setWeatherDuration(20);
                    }
                default:
                    plugin.logger(Level.WARNING, "Not implemented yet");
            }
            plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Vote passed"));
            Bukkit.getScheduler().cancelTask(countdownTaskId.get());
            resetValues(currentVoteType);
        }
    }

    private void resetValues(JVoteEnums cmd) {
        plugin.logger(Level.INFO, "Resetting values after vote ended and adding cool down");
        voteStarted.set(false);
        totalVotes.set(0);
        isVoteTimePassed.set(false);
        currentVoteType = null;
        playerHasVoted.clear();
        isOnCooldown.putIfAbsent(cmd, 0);
        Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, () -> isOnCooldown.remove(cmd),
                TimeTickConverter.ticksPerDay);
    }

    // this function will handle the timer and check that the vote has passed at 1s intervals
    // TODO: vote time should be a config value
    // TODO: reminder times should be a config value [30, 20, 10, 5] <- example
    private void doVoteTimer() {
        if (!voteStarted.get()) {
            AtomicInteger count = new AtomicInteger(60);
            voteStarted.set(true);
            countdownTaskId.set(Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
                int curr = count.getAndDecrement();
                if (curr == 0) {
                    plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Voting has ended"));
                    resetValues(currentVoteType);
                } else {
                    if (curr == 30 || curr == 20 || curr == 10 || curr == 5) {
                        plugin.getServer().broadcastMessage(JVoteUtils.printMessage(curr + " seconds remaining"));
                    }
                }
                if (checkVote()) {
                    doVote();
                    plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Vote passed"));
                    Bukkit.getScheduler().cancelTask(countdownTaskId.get());
                }
            }, 20, 20));
            plugin.logger(Level.INFO, "Scheduled task with ID: " + countdownTaskId.get());
        } else {
            plugin.logger(Level.INFO, "Vote already in progress");
        }
    }
}
