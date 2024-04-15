package com.wkaye.jvote;

import com.johnymuffin.beta.fundamentals.util.TimeTickConverter;
import com.johnymuffin.beta.fundamentals.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
            String msg = JVoteUtils.printMessage("Proper usage is &a/vote <day/night/storm/clear>");
            sender.sendMessage(msg);
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
                sender.sendMessage(JVoteUtils.printMessage("A vote is already in progress"));
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
                                + Utils.formatColor("&7 has started. Vote by doing &a/vote <yes/no>"));
                plugin.getServer().broadcastMessage(msg);
                world = player.getWorld();
                if (checkVote("yes", player)) {
                    doVote();
                } else {
                    doVoteTimer();
                }

            } catch (IllegalArgumentException e) {
                String msg = JVoteUtils.printMessage("Proper usage is &a/vote <day/night/storm/clear>");
                sender.sendMessage(msg);
                plugin.logger(Level.WARNING, "Attempted to start /vote with improper argument");
                return true;
            }
        }


        return true;
    }

    private boolean checkVote(String arg, CommandSender sender) {
        Player player = (Player) sender;
        if (playerHasVoted.contains(player)) {
            // send message to player that he/she already voted and return
            String msg = JVoteUtils.printMessage("You have already voted");
            sender.sendMessage(msg);
            return false;
        }
        if ("yes".contains(arg.toLowerCase())) {
            totalVotes.incrementAndGet();
        } else if ("no".contains(arg.toLowerCase())) {
            totalVotes.decrementAndGet();
        }
        sender.sendMessage(JVoteUtils.printMessage("You have voted"));
        playerHasVoted.add(player);
        return checkVote();
    }

    private boolean checkVote() {
        int votes = totalVotes.get();
        if (plugin.getDebugLevel() > 0) {
            plugin.logger(Level.INFO, "total votes: " + votes);
        }
        return votes > 0;
    }

    private void doVote() {
        if (currentVoteType == null) {
            plugin.logger(Level.SEVERE, "Unexpected error when getting vote type");
        } else {
            switch (currentVoteType) {
                case DAY:
                    world.setTime(TimeTickConverter.nameToTicks.get("daystart"));
                    break;
                case NIGHT:
                    world.setTime(TimeTickConverter.nameToTicks.get("nightstart"));
                    break;
                case SUN:
                case CLEAR:
                    if (world.hasStorm()) {
                        world.setThundering(false);
                        world.setWeatherDuration(5);
                    }
                    break;
                case STORM:
                    if (!world.hasStorm()) {
                        world.setWeatherDuration(5);
                    }
                    break;
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
        int cooldownTimer = JVoteConfig.getInstance().getConfigInteger("settings.cooldown-timer-ticks");
        if (cmd.equals(JVoteEnums.CLEAR) || cmd.equals(JVoteEnums.SUN)) {
            isOnCooldown.putIfAbsent(JVoteEnums.SUN, 0);
            isOnCooldown.putIfAbsent(JVoteEnums.CLEAR, 0);
            Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, () -> {
                isOnCooldown.remove(JVoteEnums.CLEAR);
                isOnCooldown.remove(JVoteEnums.SUN);
                }, cooldownTimer
            );
        } else {
            isOnCooldown.putIfAbsent(cmd, 0);
            Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, () -> isOnCooldown.remove(cmd),
                    cooldownTimer);
        }
    }

    // this function will handle the timer and check that the vote has passed at 1s intervals
    @SuppressWarnings("unchecked")
    private void doVoteTimer() {
        if (!voteStarted.get()) {
            AtomicInteger count = new AtomicInteger(JVoteConfig.getInstance().getConfigInteger("settings.timer-length"));
            voteStarted.set(true);
            countdownTaskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                int curr = count.getAndDecrement();
                if (curr == 0) {
                    plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Voting has ended"));
                    Bukkit.getScheduler().cancelTask(countdownTaskId.get());
                    resetValues(currentVoteType);
                } else {
                    ArrayList<Integer> frequencies = (ArrayList<Integer>)
                            JVoteConfig.getInstance().getConfigOption("settings.reminder-frequency");
                    if (JVoteConfig.getInstance().getConfigBoolean("settings.toggle-timer") &&
                            frequencies.contains(curr)) {
                        plugin.getServer().broadcastMessage(JVoteUtils.printMessage(curr + " seconds remaining"));
                    }
                }
                if (checkVote()) {
                    doVote();
                    Bukkit.getScheduler().cancelTask(countdownTaskId.get());
                }
            }, 20, 20));
            plugin.logger(Level.INFO, "Scheduled task with ID: " + countdownTaskId.get());
        } else {
            plugin.logger(Level.INFO, "Vote already in progress");
        }
    }
}
