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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;

public class EntityEventHandler extends EntityListener
{
	//when an entity (includes both dynamite and creepers) explodes...
	public void onEntityExplode(EntityExplodeEvent explodeEvent)
	{		
		Location location = explodeEvent.getLocation();
		
		//if it's NOT in the managed world, let it splode (or let other plugins worry about it)
		RegionCoordinates region = RegionCoordinates.fromLocation(location);
		if(region == null) return;
		
		//otherwise if it's close to a region post
		Location regionCenter = PopulationDensity.getRegionCenter(region);
		regionCenter.setY(PopulationDensity.ManagedWorld.getHighestBlockYAt(regionCenter));		
		if(regionCenter.distanceSquared(location) < 225)  //225 = 15 * 15
		{			
			explodeEvent.setCancelled(true);  //TRIVIA!  All the noise and terror, none of the destruction (whew!).			
		}
		
		//NOTE!  Why not distance?  Because distance squared is cheaper and will be good enough for this.
	}	
}
