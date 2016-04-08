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

import org.bukkit.Location;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

//teleports a player.  useful as scheduled task so that a joining player may be teleported (otherwise error)
class TeleportPlayerTask implements Runnable 
{	
	private Player player;
	private Location destination;
	
	public TeleportPlayerTask(Player player, Location destination)
	{
		this.player = player;
		this.destination = destination;
	}
	
	@Override
	public void run()
	{
		ArrayList<Entity> entitiesToTeleport = new ArrayList<Entity>();
		entitiesToTeleport.add(player);

		List<Entity> nearbyEntities = player.getNearbyEntities(10, 10, 10);
		for(Entity entity : nearbyEntities)
		{
            if(entity instanceof Tameable)
            {
                Tameable tameable = (Tameable) entity;
                if(tameable.isTamed())
                {
                    AnimalTamer tamer = tameable.getOwner();
                    if(tamer != null && player.getUniqueId().equals(tamer.getUniqueId()))
                    {
                        entitiesToTeleport.add(entity);
                    }
                }
            }
            else if(entity instanceof Animals)
            {
                Animals animal = (Animals)entity;
                if(animal.getTarget() != null && animal.getTarget().equals(player))
                {
                    entitiesToTeleport.add(animal);
                }
            }
            else if(entity instanceof Creature)
		    {
		        Creature creature = (Creature) entity;
		        if(creature.isLeashed() && player.equals(creature.getLeashHolder()) || player.equals(creature.getPassenger()))
		        {
		            entitiesToTeleport.add(creature);
		        }
		    }
		}
		
	    for(Entity entity : entitiesToTeleport)
	    {
	        entity.teleport(destination, TeleportCause.PLUGIN);
	    }
	}
}
