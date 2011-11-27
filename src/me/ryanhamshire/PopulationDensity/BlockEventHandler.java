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

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockEventHandler extends BlockListener 
{
	private DataStore dataStore;
	
	//boring typical constructor
	public BlockEventHandler(DataStore dataStore)
	{
		this.dataStore = dataStore;
	}
	
	//when a player breaks a block...
	public void onBlockBreak(BlockBreakEvent breakEvent)
	{
		Player player = breakEvent.getPlayer();
		Block block = breakEvent.getBlock();
		
		//if the player is not in managed world, do nothing (let vanilla code and other plugins do whatever)
		if(!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;
		
		//otherwise figure out which region that block is in
		Location blockLocation = block.getLocation();
		RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation); 
		
		//if that block isn't in the player's home region and he doesn't have special permissions, block the break (instead of break the block?  comedy.)
		if(!player.hasPermission("populationdensity.buildbreakanywhere") && !PopulationDensity.instance.buildBreakAnywhere && !blockRegion.equals(this.dataStore.getHomeRegionCoordinates(player)))
		{
			player.sendMessage("You can't break blocks outside your home region.  To move in here, use /movein.");
			breakEvent.setCancelled(true);  //NO SIR!
			return;
		}
		
		//if too close to (or above) region post, send an error message
		//note the ONLY way to edit around a region post is to have special permission
		if(!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion))
		{
			player.sendMessage("You can't break blocks this close to the region post.");
			breakEvent.setCancelled(true);
			return;
		}
	}
	
	//COPY PASTE!  this is practically the same as the above block break handler
	public void onBlockPlace(BlockPlaceEvent placeEvent)
	{
		Player player = placeEvent.getPlayer();
		Block block = placeEvent.getBlock();
		
		//if not in managed world, do nothing
		if(!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;
		
		Location blockLocation = block.getLocation();
		RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation); 
		
		//if not in home region, send an error message
		if(!player.hasPermission("populationdensity.buildbreakanywhere") && !PopulationDensity.instance.buildBreakAnywhere && !blockRegion.equals(this.dataStore.getHomeRegionCoordinates(player)))
		{
			player.sendMessage("You build outside your home region.  To move in here, use /movein.");
			placeEvent.setCancelled(true);
			return;
		}
		
		//if too close to (or above) region post, send an error message
		if(!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion))
		{
			player.sendMessage("You can't build this close to the region post.");
			placeEvent.setCancelled(true);
			return;
		}
	}
	
	//determines whether or not you're "near" a region post
	//has to be pretty restrictive to make grief via lava difficult to pull off
	private boolean nearRegionPost(Location location, RegionCoordinates region)
	{
		Location postLocation = PopulationDensity.getRegionCenter(region);
		
		//NOTE!  Why not use distance?  Because I want a box to the sky, not a sphere.
		//Why not round?  Below calculation is cheaper than distance (needed for a cylinder or sphere).
		//Why to the sky?  Because if somebody builds a platform above the post, folks will teleport onto that platform by mistake.
		//Also...  lava from above would be bad.
		//Why not below?  Because I can't imagine mining beneath a post as an avenue for griefing. 
		
		return (	location.getBlockX() > postLocation.getBlockX() - 10 &&
					location.getBlockX() < postLocation.getBlockX() + 10 &&
					location.getBlockZ() > postLocation.getBlockZ() - 10 &&
					location.getBlockZ() < postLocation.getBlockZ() + 10 &&
					location.getBlockY() > PopulationDensity.ManagedWorld.getHighestBlockYAt(postLocation) - 5
				);
	}
}
