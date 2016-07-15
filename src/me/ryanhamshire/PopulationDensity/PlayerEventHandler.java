/*
    PopulationDensity Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.PopulationDensity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;

public class PlayerEventHandler implements Listener {
	private DataStore dataStore;

	// queue of players waiting to join the server
	public ArrayList<LoginQueueEntry> loginQueue = new ArrayList<LoginQueueEntry>();

	// typical constructor, yawn
	public PlayerEventHandler(DataStore dataStore, PopulationDensity plugin) {
		this.dataStore = dataStore;
	}

	// when a player attempts to join the server...
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerLoginEvent(PlayerLoginEvent event)
	{
		if (!PopulationDensity.instance.enableLoginQueue)
			return;

		if (event.getResult() != Result.ALLOWED)
			return;

		Player player = event.getPlayer();
		PlayerData playerData = this.dataStore.getPlayerData(player);
		/*
		 * PopulationDensity.AddLogEntry("");
		 * PopulationDensity.AddLogEntry("QUEUE STATUS================");
		 * PopulationDensity.AddLogEntry("");
		 * 
		 * for(int i = 0; i < this.loginQueue.size(); i++) { LoginQueueEntry
		 * entry = this.loginQueue.get(i); DateFormat timeFormat =
		 * DateFormat.getTimeInstance(DateFormat.SHORT);
		 * PopulationDensity.AddLogEntry("\t" + entry.playerName + " " +
		 * entry.priority + " " + timeFormat.format((new
		 * Date(entry.lastRefreshed)))); }
		 * 
		 * PopulationDensity.AddLogEntry("");
		 * PopulationDensity.AddLogEntry("END QUEUE STATUS================");
		 * PopulationDensity.AddLogEntry("");
		 * 
		 * PopulationDensity.AddLogEntry("attempting to log in " +
		 * player.getName());
		 */

		@SuppressWarnings("unchecked")
        Collection<Player> playersOnline = (Collection<Player>)PopulationDensity.instance.getServer().getOnlinePlayers();
		int totalSlots = PopulationDensity.instance.getServer().getMaxPlayers();

		// determine player's effective priority
		int effectivePriority = playerData.loginPriority;

		// PopulationDensity.AddLogEntry("\tlogin priority " +
		// playerData.loginPriority);

		// if the player last disconnected within the last two minutes, treat
		// the player with very high priority
		Calendar twoMinutesAgo = Calendar.getInstance();
		twoMinutesAgo.add(Calendar.MINUTE, -2);
		if (playerData.lastDisconnect.compareTo(twoMinutesAgo.getTime()) == 1 && playerData.loginPriority < 99) {
			effectivePriority = 99;
		}

		// cap priority at 100
		if (effectivePriority > 100)
			effectivePriority = 100;

		// PopulationDensity.AddLogEntry("\teffective priority " +
		// effectivePriority);

		// if the player has maximum priority
		if (effectivePriority > 99) {
			// PopulationDensity.AddLogEntry("\thas admin level priority");

			// if there's room, log him in without consulting the queue
			if (playersOnline.size() <= totalSlots - 2) {
				// PopulationDensity.AddLogEntry("\tserver has room, so instant login");
				return;
			}
		}

		// scan the queue for the player, removing any expired queue entries
		long nowTimestamp = Calendar.getInstance().getTimeInMillis();

		int queuePosition = -1;
		for (int i = 0; i < this.loginQueue.size(); i++) {
			LoginQueueEntry entry = this.loginQueue.get(i);

			// if this entry has expired, remove it
			if ((nowTimestamp - entry.lastRefreshed) > 180000 /* three minutes */) {
				// PopulationDensity.AddLogEntry("\t\tremoved expired entry for "
				// + entry.playerName);
				this.loginQueue.remove(i--);
			}

			// otherwise compare the name in the entry
			else if (entry.playerName.equals(player.getName())) {
				queuePosition = i;
				// PopulationDensity.AddLogEntry("\t\trefreshed existing entry at position "
				// + queuePosition);
				entry.lastRefreshed = nowTimestamp;
				break;
			}
		}

		// if not in the queue, find the appropriate place in the queue to
		// insert
		if (queuePosition == -1) {
			// PopulationDensity.AddLogEntry("\tnot in the queue ");
			if (this.loginQueue.size() == 0) {
				// PopulationDensity.AddLogEntry("\tqueue empty, will insert in position 0");
				queuePosition = 0;
			} else {
				// PopulationDensity.AddLogEntry("\tsearching for best place based on rank");
				for (int i = this.loginQueue.size() - 1; i >= 0; i--) {
					LoginQueueEntry entry = this.loginQueue.get(i);

					if (entry.priority >= effectivePriority) {
						queuePosition = i + 1;
						// PopulationDensity.AddLogEntry("\tinserting in position"
						// + queuePosition + " behind " + entry.playerName +
						// ", pri " + entry.priority);
						break;
					}
				}

				if (queuePosition == -1)
					queuePosition = 0;
			}

			this.loginQueue.add(queuePosition,
					new LoginQueueEntry(player.getName(), effectivePriority,
							nowTimestamp));
		}

		// PopulationDensity.AddLogEntry("\tplayer count " +
		// playersOnline.length + " / " + totalSlots);

		// if the player can log in
		if (totalSlots - 1 - playersOnline.size()
				- PopulationDensity.instance.reservedSlotsForAdmins > queuePosition) {
			// PopulationDensity.AddLogEntry("\tcan log in now, removed from queue");

			// remove from queue
			this.loginQueue.remove(queuePosition);

			// allow login
			return;
		}

		else {
			// otherwise, kick, notify about position in queue, and give
			// instructions
			// PopulationDensity.AddLogEntry("\tcant log in yet");
			event.setResult(Result.KICK_FULL);
			String kickMessage = PopulationDensity.instance.queueMessage;
			kickMessage = kickMessage.replace("%queuePosition%",
					String.valueOf(queuePosition + 1));
			kickMessage = kickMessage.replace("%queueLength%",
					String.valueOf(this.loginQueue.size()));
			event.setKickMessage(""
					+ (queuePosition + 1)
					+ " of "
					+ this.loginQueue.size()
					+ " in queue.  Reconnect within 3 minutes to keep your place.  :)");
			event.disallow(event.getResult(), event.getKickMessage());
		}
	}

	// when a player successfully joins the server...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		
		Player joiningPlayer = event.getPlayer();
		
		PopulationDensity.instance.resetIdleTimer(joiningPlayer);
		
		PlayerData playerData = this.dataStore.getPlayerData(joiningPlayer);
		if (playerData.lastObservedLocation == null) {
			playerData.lastObservedLocation = joiningPlayer.getLocation();
		}

		// if the player doesn't have a home region yet (he hasn't logged in
		// since the plugin was installed)
		RegionCoordinates homeRegion = playerData.homeRegion;
		if (homeRegion == null)
		{
		    // if he's never been on the server before
		    if(!joiningPlayer.hasPlayedBefore())
		    {
    			// his home region is the open region
    			RegionCoordinates openRegion = this.dataStore.getOpenRegion();
    			playerData.homeRegion = openRegion;
    			PopulationDensity.AddLogEntry("Assigned new player "
    					+ joiningPlayer.getName() + " to region "
    					+ this.dataStore.getRegionName(openRegion) + " at "
    					+ openRegion.toString() + ".");
    
    			// entirely new players who've not visited the server before will
    			// spawn in their home region by default.
    			// if configured as such, teleport him there in a couple of seconds
    			if (PopulationDensity.instance.newPlayersSpawnInHomeRegion && joiningPlayer.getLocation().distanceSquared(joiningPlayer.getWorld().getSpawnLocation()) < 625) 
    			{
    				PlaceNewPlayerTask task = new PlaceNewPlayerTask(joiningPlayer, playerData.homeRegion);
    				PopulationDensity.instance.getServer().getScheduler().scheduleSyncDelayedTask(PopulationDensity.instance, task, 1L);
    			}
    			
    			// otherwise allow other plugins to control spawning a new player
    			else
    			{
    			    // unless pop density is configured to force a precise world spawn point
    			    if(PopulationDensity.instance.preciseWorldSpawn)
    			    {
    			        TeleportPlayerTask task = new TeleportPlayerTask(joiningPlayer, joiningPlayer.getWorld().getSpawnLocation(), false);
    			        PopulationDensity.instance.getServer().getScheduler().scheduleSyncDelayedTask(PopulationDensity.instance, task, 1L);
    			    }
    			    
    			    // always remove monsters around the new player's spawn point to prevent ambushes
    			    PopulationDensity.removeMonstersAround(joiningPlayer.getWorld().getSpawnLocation());
    			}
		    }
		    
		    //otherwise if he's played before, guess his home region as best we can
		    else
		    {
		        if(joiningPlayer.getBedSpawnLocation() != null)
		        {
		            playerData.homeRegion = RegionCoordinates.fromLocation(joiningPlayer.getBedSpawnLocation());
		        }
		        else
		        {
		            playerData.homeRegion = RegionCoordinates.fromLocation(joiningPlayer.getLocation());
		        }
		        
		        if(playerData.homeRegion == null)
		        {
		            playerData.homeRegion = PopulationDensity.instance.dataStore.getOpenRegion();
		        }
		    }
		    
		    this.dataStore.savePlayerData(joiningPlayer, playerData);
		}
	}

	// when a player disconnects...
	@EventHandler(ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		this.onPlayerDisconnect(event.getPlayer());
	}

	// when a player gets kicked...
	@EventHandler(ignoreCancelled = true)
	public void onPlayerKicked(PlayerKickEvent event)
	{
		this.onPlayerDisconnect(event.getPlayer());
	}

	// when a player disconnects...
	private void onPlayerDisconnect(Player player)
	{
		PlayerData playerData = this.dataStore.getPlayerData(player);

		// note logout timestamp
		playerData.lastDisconnect = Calendar.getInstance().getTime();
		
		//note login priority based on permissions
		// assert permission-based priority
		if (player.hasPermission("populationdensity.prioritylogin")
				&& playerData.loginPriority < 25) {
			playerData.loginPriority = 25;
		}

		if (player.hasPermission("populationdensity.elitelogin")
				&& playerData.loginPriority < 50) {
			playerData.loginPriority = 50;
		}

		// if the player has kicktologin permission, treat the player with
		// highest priority
		if (player.hasPermission("populationdensity.adminlogin")) {
			// PopulationDensity.AddLogEntry("\tcan fill administrative slots");
			playerData.loginPriority = 100;
		}
		
		this.dataStore.savePlayerData(player, playerData);

		// cancel any existing afk check task
		if (playerData.afkCheckTaskID >= 0) {
			PopulationDensity.instance.getServer().getScheduler()
					.cancelTask(playerData.afkCheckTaskID);
			playerData.afkCheckTaskID = -1;
		}
		
				// clear any cached data for this player in the data store
		this.dataStore.clearCachedPlayerData(player);
	}

	// when a player respawns after death...
	@EventHandler(ignoreCancelled = true)
	public void onPlayerRespawn(PlayerRespawnEvent respawnEvent)
	{
		if (!PopulationDensity.instance.respawnInHomeRegion)
		{
		    if(PopulationDensity.ManagedWorld == respawnEvent.getRespawnLocation().getWorld())
		    {
		        PopulationDensity.removeMonstersAround(respawnEvent.getRespawnLocation());
		    }
		    return;
		}
		
		Player player = respawnEvent.getPlayer();
		
		// if it's NOT a bed respawn, redirect it to the player's home region
		// post
		// this keeps players near where they live, even when they die (haha)
		if (!respawnEvent.isBedSpawn())
		{
		    PlayerData playerData = this.dataStore.getPlayerData(player);

			// find the center of his home region
			Location homeRegionCenter = PopulationDensity.getRegionCenter(playerData.homeRegion, false);

			// aim for two blocks above the highest block and teleport
			homeRegionCenter.setY(PopulationDensity.ManagedWorld
					.getHighestBlockYAt(homeRegionCenter) + 2);
			respawnEvent.setRespawnLocation(homeRegionCenter);
			
			PopulationDensity.removeMonstersAround(homeRegionCenter);
		}
	}
	
	@EventHandler(ignoreCancelled = true)
    public synchronized void onPlayerChat(AsyncPlayerChatEvent event)
    {
        String msg = event.getMessage();
        
        if(msg.equalsIgnoreCase(PopulationDensity.instance.dataStore.getMessage(Messages.Lag)))
        {
            Player player = event.getPlayer();
            
            event.getRecipients().clear();
            event.getRecipients().add(player);
            
            PopulationDensity.instance.reportTPS(player);
        }
    }
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event)
	{
	    Entity entity = event.getRightClicked();
        if(entity instanceof Animals || entity instanceof Minecart || entity instanceof Villager)
	    {
	        entity.setTicksLived(1);
	    }
	}
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPlayerToggleFlight(PlayerToggleFlightEvent event)
	{
	    if(PopulationDensity.instance.isFallDamageImmune(event.getPlayer()))
	    {
	        event.setCancelled(true);
	    }
	}
}
