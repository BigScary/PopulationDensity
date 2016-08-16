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

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

public class WorldEventHandler implements Listener
{
	//when a chunk loads, generate a region post in that chunk if necessary
	@EventHandler(ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent chunkLoadEvent)
	{		
		Chunk chunk = chunkLoadEvent.getChunk();
		
		Entity [] entities = chunk.getEntities();
	    for(Entity entity : entities)
	    {
	        //entities which appear abandoned on chunk load get the grandfather clause treatment
	        if(isAbandoned(entity))
	        {
	            entity.setTicksLived(1);
	        }
	        
	        //skeletal horses never go away unless slain.  on chunk load, remove any which aren't leashed or carrying a rider
	        if(entity.getType() == EntityType.HORSE && PopulationDensity.instance.removeWildSkeletalHorses)
	        {
	            Horse horse = (Horse)entity;
	            if(horse.getVariant() == Horse.Variant.SKELETON_HORSE && !horse.isLeashed() && horse.getPassenger() == null && horse.getCustomName() == null)
	            {
	                ItemStack saddleStack = horse.getInventory().getSaddle();
	                if(saddleStack == null || saddleStack.getType() != Material.SADDLE)
	                {
	                    horse.setHealth(0);
	                }
	            }
	        }
	    }
		
		//nothing more to do in worlds other than the managed world
		if(chunk.getWorld() != PopulationDensity.ManagedWorld) return;
		
		//find the boundaries of the chunk
		Location lesserCorner = chunk.getBlock(0, 0, 0).getLocation();
		Location greaterCorner = chunk.getBlock(15, 0, 15).getLocation();
		
		//find the center of this chunk's region
		RegionCoordinates region = RegionCoordinates.fromLocation(lesserCorner);		
		Location regionCenter = PopulationDensity.getRegionCenter(region, false);
		
		//if the chunk contains the region center
		if(	regionCenter.getBlockX() >= lesserCorner.getBlockX() && regionCenter.getBlockX() <= greaterCorner.getBlockX() &&
			regionCenter.getBlockZ() >= lesserCorner.getBlockZ() && regionCenter.getBlockZ() <= greaterCorner.getBlockZ())
		{
			//create a task to build the post after 10 seconds
			try
			{
			    PopulationDensity.instance.dataStore.AddRegionPost(region);
			}
			catch(ChunkLoadException e){}  //this should never happen, because the chunk is loaded (why else would onChunkLoad() be invoked?)
		}
	}
	
	static boolean isAbandoned(Entity entity)
	{
	    String customName = entity.getCustomName();
	    if(customName != null && !customName.isEmpty()) return false;
        if((entity instanceof Tameable)) return false;
        
        final int DAY_IN_TICKS = 1728000;
        
        int darkMaxTicks = DAY_IN_TICKS;
        int lightMaxTicks = DAY_IN_TICKS * 3;
        
        //determine how long to wait for player interaction before deciding an entity is abandoned
        if(entity instanceof Animals && PopulationDensity.instance.abandonedFarmAnimalsDie)
        {
            //can afford to be aggressive with animals because they're easy to re-breed
            darkMaxTicks = DAY_IN_TICKS;
            lightMaxTicks = DAY_IN_TICKS * 3;
        }
        else if(entity instanceof Minecart && PopulationDensity.instance.unusedMinecartsVanish)
        {
            darkMaxTicks = lightMaxTicks = DAY_IN_TICKS * 7;
        }
        else  //if not one of the above, don't remove it even if long-lived without interaction
        {
            return false;
        }
        
        //triple allowance if a player nametagged the entity
        if(customName != null && !customName.isEmpty())
        {
            darkMaxTicks *= 3;
            lightMaxTicks *= 3;
        }
        
        //if in the dark, treat as wilderness creature which won't live as long
        byte lightLevel = 15;
        int yLocation = entity.getLocation().getBlockY();
        if (yLocation < 255 && yLocation > 0)
        {
            lightLevel = entity.getLocation().getBlock().getLightFromBlocks();
        }
        
        if(lightLevel < 4 && entity.getTicksLived() > darkMaxTicks)  //in the dark
        {
            return true;
        }
        else if(entity.getTicksLived() > lightMaxTicks) //in the light 
        {
            //only remove if there are at least two similar entities nearby, to allow for rebreeding later
            if(!(entity instanceof Minecart))  //doesn't apply to minecarts, they're more easily replaced
            {
                List<Entity> nearbyEntities = entity.getNearbyEntities(15, 15, 15);
                int nearbySimilar = 0;
                for(Entity nearby : nearbyEntities)
                {
                    if(nearby.getType() == entity.getType() && !nearby.hasMetadata("pd_removed"))
                    {
                        nearbySimilar++;
                        if(nearbySimilar > 1)
                        {
                            return true;
                        }
                    }
                }
            }
            else
            {
                return true;
            }
        }
        
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event)
    {       
        Chunk chunk = event.getChunk();
        
        //expire any abandoned animals
        removeAbandonedEntities(chunk);
        
        //nothing more to do in worlds other than the managed world
        if(chunk.getWorld() != PopulationDensity.ManagedWorld) return;
        
        //don't allow the new player spawn point chunk to unload
        
        //find the boundaries of the chunk
        Location lesserCorner = chunk.getBlock(0, 0, 0).getLocation();
        Location greaterCorner = chunk.getBlock(15, 0, 15).getLocation();
        
        //if the region is the new player region
        RegionCoordinates region = RegionCoordinates.fromLocation(lesserCorner);
        if(region.equals(PopulationDensity.instance.dataStore.getOpenRegion()))
        {
            Location regionCenter = PopulationDensity.getRegionCenter(region, false);
        
            //if the chunk contains the region center
            if( regionCenter.getBlockX() >= lesserCorner.getBlockX() && regionCenter.getBlockX() <= greaterCorner.getBlockX() &&
                    regionCenter.getBlockZ() >= lesserCorner.getBlockZ() && regionCenter.getBlockZ() <= greaterCorner.getBlockZ())
            {
                //don't unload the chunk
                event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    static void removeAbandonedEntities(Chunk chunk)
    {
        Entity [] entities = chunk.getEntities();
        for(int i = 0; i < entities.length; i++)
        {
            Entity entity = entities[i];
            if(isAbandoned(entity))
            {
                if(PopulationDensity.instance.markRemovedEntityLocations)
                {
                    Block block = entity.getLocation().getBlock();
                    Material blockType = block.getType();
                    if(blockType == Material.LONG_GRASS || blockType == Material.AIR)
                    {
                        block.setTypeIdAndData(31, (byte)2, false);  //fern
                    }
                }
                
                //eject any riders (for pigs, minecarts)
                entity.eject();
                
                //entity.remove() removes on next tick, so must mark removed entities with metadata so we know which were removed this tick
                entity.setMetadata("pd_removed", new FixedMetadataValue(PopulationDensity.instance, true));
                entity.remove();
            }
        }
    }   
}