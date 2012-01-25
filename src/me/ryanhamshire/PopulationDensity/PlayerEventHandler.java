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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;

public class PlayerEventHandler extends PlayerListener 
{
	private DataStore dataStore;
	
	//typical constructor, yawn
	public PlayerEventHandler(DataStore dataStore, PopulationDensity plugin)
	{
		this.dataStore = dataStore;
	}
	
	//when a player connects to the server...
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Player joiningPlayer = event.getPlayer();
		
		//if this plugin has never seen this player before
		if(this.dataStore.getHomeRegionCoordinates(joiningPlayer) == null)
		{
			//try to kick off a resource scan of the open region
			//if it's been less than 6 hours since the last scan, this won't do anything
			PopulationDensity.instance.updateOpenRegion();
			
			//NOTES!  Why not set the player's home region to the open region and teleport him there?
			//Because Bukkit will FREAK OUT and kick him when it (after teleportation) tries to add him to the default world.
			//OBSERVATION!  When players log in, they generally spawn above the ground and fall a bit.
			//WORKAROUND!  List for that initial move event, and teleport the player THEN.  See onPlayerMove() below.
		}
		
		//otherwise we know this player from a previous play session
		else
		{
			//if logging into the managed world
			if(joiningPlayer.getWorld().equals(PopulationDensity.ManagedWorld))
			{
				//notify players in landing region of arrival
				PopulationDensity.instance.notifyRegionChange(joiningPlayer, null, RegionCoordinates.fromLocation((joiningPlayer.getLocation())));
			}
		}
	}
	
	//when a player moves...
	public void onPlayerMove(PlayerMoveEvent event)
	{
		Player player = event.getPlayer();
		
		//if the player doesn't have a home region yet (he hasn't logged in since the plugin was installed)
		RegionCoordinates homeRegion = this.dataStore.getHomeRegionCoordinates(player);
		if(homeRegion == null)
		{
			//his home region is the open region
			RegionCoordinates openRegion = this.dataStore.getOpenRegion();
			this.dataStore.setHomeRegionCoordinates(player, openRegion);
			PopulationDensity.AddLogEntry("Assigned new player " + player.getName() + " to region " + this.dataStore.getRegionName(openRegion) + " at " + openRegion.toString() + ".");
			
			//entirely new players who've not visited the server before will spawn at the default spawn
			//if configured as such, teleport him there right away
			//because the world takes a while to load after login, he'll never know he was teleported
			if(PopulationDensity.instance.newPlayersSpawnInHomeRegion && player.getLocation().distanceSquared(player.getWorld().getSpawnLocation()) < 625)
			{
				PopulationDensity.instance.TeleportPlayer(player, openRegion);
			}
			
			return;
		}
		
		//otherwise, if we're just watching movement to notify players as they wander into new regions
		if(!PopulationDensity.instance.buildBreakAnywhere)
		{
			Location to = event.getTo();
			Location from = event.getFrom();		
			
			//figure out which region he was in before he moved, and where he is after the move
			//note that these methods will return NULL when the location passed in is not in the managed world
			RegionCoordinates previousRegion = RegionCoordinates.fromLocation(from);
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(to);
					
			//if both locations are outside the managed world, don't do anything
			if(currentRegion == null && previousRegion == null)
			{
				return;
			}
			
			//otherwise if crossed a boundary in the managed world
			if(previousRegion != null && !previousRegion.equals(currentRegion) || currentRegion != null && !currentRegion.equals(previousRegion))
			{		
				//tell him and everyone in the two regions about the change
				PopulationDensity.instance.notifyRegionChange(player, previousRegion, currentRegion);
			}
		}
	}
	
	//when a player disconnects...
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		Player player = event.getPlayer();
		
		//if logging-out from within the managed world
		if(player.getWorld().equals(PopulationDensity.ManagedWorld))
		{
			//notify any players in the region about the departure
			PopulationDensity.instance.notifyRegionChange(player, RegionCoordinates.fromLocation(player.getLocation()), null);
		}
	}
	
	//when a player respawns after death...
	//note that when not configured to control respawn, this code doesn't run because we don't register for the event
	//( see PopulationDensity.onEnabled() )
	public void onPlayerRespawn(PlayerRespawnEvent respawnEvent)
	{
		Player player = respawnEvent.getPlayer();
		
		//if it's NOT a bed respawn, redirect it to the player's home region post
		//this keeps players near where they live, even when they die (haha)
		if(!respawnEvent.isBedSpawn())
		{
			//find the center of his home region
			RegionCoordinates homeRegion = this.dataStore.getHomeRegionCoordinates(player);			
			Location homeRegionCenter = PopulationDensity.getRegionCenter(homeRegion);
			
			//aim for two blocks above the highest block and teleport
			homeRegionCenter.setY(PopulationDensity.ManagedWorld.getHighestBlockYAt(homeRegionCenter) + 2);
			respawnEvent.setRespawnLocation(homeRegionCenter);			
		}
	}
	
	//when a player uses a portal...
	public void onPlayerPortal(PlayerPortalEvent event)
	{
		//figure out the from and to
		Location from = event.getFrom();
		Location to = event.getTo();
		
		//if the server doesn't know where to send the player and he's leaving from the managed world, manually set the destination
		if(to == null && from.getWorld().equals(PopulationDensity.ManagedWorld))
		{
			if(from.getBlock().getType() == Material.PORTAL)
			{
				Location destination = new Location(PopulationDensity.ManagedWorldNether, from.getX() * 0.125, from.getY() * 0.125, from.getZ() * 0.125);
				event.setTo(destination);
			}
			
			//if it's an end portal
			else if(from.getBlock().getType() == Material.ENDER_PORTAL)
			{
				Location destination = new Location(PopulationDensity.ManagedWorldEnd, 50, 100, 50);
				Chunk chunk = PopulationDensity.ManagedWorldEnd.getChunkAt(destination);
				chunk.load(true);
				
				event.setTo(destination);
			}
			
			//if we can't determine where to send the player, don't send him anywhere
			else
			{
				event.setCancelled(true);
			}
			
		}
		
		//otherwise if the server doesn't know where to send him and he's leaving from the nether world we created
		else if (to == null && from.getWorld().equals(PopulationDensity.ManagedWorldNether))
		{
			//manually set destination
			Location destination = new Location(PopulationDensity.ManagedWorld, from.getX() * 8, from.getY() * 8, from.getZ() * 8);
			event.setTo(destination);
		}		
	}
}
