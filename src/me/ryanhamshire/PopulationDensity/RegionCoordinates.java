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

public class RegionCoordinates 
{
	public int x;
	public int z;
	
	//basic boring stuff (yawn)
	public RegionCoordinates(int x, int z)	
	{
		this.x = x;
		this.z = z;		
	}
	
	//given a location, returns the coordinates of the region containing that location
	//returns NULL when the location is not in the managed world
	//TRIVIA!  despite the simplicity of this method, I got it badly wrong like 5 times before it was finally fixed
	public static RegionCoordinates fromLocation(Location location)
	{
		//not managed world?  return null
		if(!location.getWorld().equals(PopulationDensity.ManagedWorld)) return null;		
		
		//keeping all regions the same size and arranging them in a strict grid makes this calculation supa-fast!
		//that's important because we do it A LOT as players move, build, break blocks, and more
		int x = location.getBlockX() / PopulationDensity.REGION_SIZE;
		if(location.getX() < 0) x--;
		
		int z = location.getBlockZ() / PopulationDensity.REGION_SIZE;
		if(location.getZ() < 0) z--;
		
		return new RegionCoordinates(x, z);		
	}
	
	//converts a string representing region coordinates to a proper region coordinates object
	//used in reading data from files and converting filenames themselves in some cases
	public RegionCoordinates(String string)
	{
		//split the input string on the space
		String [] elements = string.split(" ");
	    
		//expect two elements - X and Z, respectively
		String xString = elements[0];
	    String zString = elements[1];
	    
	    //convert those to integer values
	    this.x = Integer.parseInt(xString);
	    this.z = Integer.parseInt(zString);
	}
	
	//opposite of above - converts region coordinates to a handy string
	public String toString()
	{
		return Integer.toString(this.x) + " " + Integer.toString(this.z);
	}
	
	//compares two region coordinates to see if they match
	@Override
	public boolean equals(Object coordinatesToCompare)
	{
		if(coordinatesToCompare == null) return false;
		
		if(!(coordinatesToCompare instanceof RegionCoordinates)) return false;
		
		RegionCoordinates coords = (RegionCoordinates)coordinatesToCompare;
		
		return this.x == coords.x && this.z == coords.z;
	}
	
	@Override
	public int hashCode()
	{
	    return this.toString().hashCode();
	}
}
