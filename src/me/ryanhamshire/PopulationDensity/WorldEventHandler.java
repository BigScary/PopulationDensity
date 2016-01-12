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
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class WorldEventHandler implements Listener
{
	//when a chunk loads, generate a region post in that chunk if necessary
	@EventHandler(ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent chunkLoadEvent)
	{		
		Chunk chunk = chunkLoadEvent.getChunk();
		
		//animals which appear abandoned on chunk load get the grandfather clause treatment
		if(PopulationDensity.instance.abandonedFarmAnimalsDie)
		{
		    Entity [] entities = chunk.getEntities();
		    for(Entity entity : entities)
		    {
		        if(isAbandonedFarmAnimal(entity))
		        {
		            entity.setTicksLived(1);
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
	
	static boolean isAbandonedFarmAnimal(Entity entity)
	{
	    String customName = entity.getCustomName();
	    if(customName != null && !customName.isEmpty()) return false;
        if(!(entity instanceof Animals) || (entity instanceof Tameable)) return false;
        
        //if in the dark, treat as wilderness animal
        byte lightLevel = entity.getLocation().getBlock().getLightFromBlocks();
        if(lightLevel < 4 && entity.getTicksLived() > 1728000)  //in the dark and 1 day in ticks without player interaction
        {
            return true;
        }
        else if(entity.getTicksLived() > 5184000) //in the light and 3 days in ticks without player interaction 
        {
            //only remove if there are at least two similar animals nearby, to allow for rebreeding later
            List<Entity> nearbyEntities = entity.getNearbyEntities(15, 15, 15);
            int nearbySimilar = 0;
            for(Entity nearby : nearbyEntities)
            {
                if(nearby.getType() == entity.getType())
                {
                    nearbySimilar++;
                    if(nearbySimilar > 1)
                    {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    //don't allow the new player spawn point chunk to unload
    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event)
    {       
        Chunk chunk = event.getChunk();
        
        //expire any abandoned animals
        if(PopulationDensity.instance.abandonedFarmAnimalsDie)
        {
            Entity [] entities = chunk.getEntities();
            ArrayList<Entity> toRemove = new ArrayList<Entity>();
            for(Entity entity : entities)
            {
                if(isAbandonedFarmAnimal(entity))
                {
                    PopulationDensity.AddLogEntry("Removed abandoned " + entity.getType().name() + " @ " + entity.getLocation().toString());
                    toRemove.add(entity);
                }
            }
            
            for(Entity entity : toRemove)
            {
                entity.remove();
            }
        }
        
        //nothing more to do in worlds other than the managed world
        if(chunk.getWorld() != PopulationDensity.ManagedWorld) return;
        
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
}