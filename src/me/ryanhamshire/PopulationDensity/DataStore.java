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

import java.io.*;
import java.text.DateFormat;
import java.util.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class DataStore 
{
	//in-memory cache for player home region, because it's needed very frequently
	//writes here also write immediately to file
	private HashMap<String, PlayerData> playerNameToPlayerDataMap = new HashMap<String, PlayerData>();
	
	//path information, for where stuff stored on disk is well...  stored
	private final static String dataLayerFolderPath = "plugins" + File.separator + "PopulationDensityData";
	private final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
	private final static String regionDataFolderPath = dataLayerFolderPath + File.separator + "RegionData";
	public final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
	
	//currently open region
	private RegionCoordinates openRegionCoordinates;
	
	//coordinates of the next region which will be opened, if one needs to be opened
	private RegionCoordinates nextRegionCoordinates;
	
	//total number of regions
	private int regionCount;
	
	//initialization!
	public DataStore()
	{
		//ensure data folders exist
		new File(playerDataFolderPath).mkdirs();
		new File(regionDataFolderPath).mkdirs();
		
		//study region data and initialize both this.openRegionCoordinates and this.nextRegionCoordinates
		this.regionCount = this.findNextRegion();
		
		//if no regions were loaded, create the first one
		if(regionCount == 0)
		{
			PopulationDensity.AddLogEntry("Please be patient while I search for a good new player starting point!");
			PopulationDensity.AddLogEntry("This initial scan could take a while, especially for worlds where players have already been building.");
			this.addRegion();			
		}
		
		PopulationDensity.AddLogEntry("Open region: \"" + this.getRegionName(this.getOpenRegion()) + "\" at " + this.getOpenRegion().toString() + ".");		
	}
	
	//used in the spiraling code below (see findNextRegion())
	private enum Direction { left, right, up, down }	
	
	//starts at region 0,0 and spirals outward until it finds a region which hasn't been initialized
	//sets private variables for openRegion and nextRegion when it's done
	//this may look like black magic, but seriously, it produces a tight spiral on a grid
	//coding this made me reminisce about seemingly pointless computer science exercises in college
	public int findNextRegion()
	{
		//spiral out from region coordinates 0, 0 until we find coordinates for an uninitialized region
		int x = 0; int z = 0;
		
		//keep count of the regions encountered
		int regionCount = 0;

		//initialization
		Direction direction = Direction.down;   //direction to search
		int sideLength = 1;  					//maximum number of regions to move in this direction before changing directions
		int side = 0;        					//increments each time we change directions.  this tells us when to add length to each side
		this.openRegionCoordinates = null;
		this.nextRegionCoordinates = new RegionCoordinates(0, 0);

		//while the next region coordinates are taken, walk the spiral
		while ((this.getRegionName(this.nextRegionCoordinates)) != null)
		{
			//loop for one side of the spiral
			for (int i = 0; i < sideLength && this.getRegionName(this.nextRegionCoordinates) != null; i++)
			{
				regionCount++;
				
				//converts a direction to a change in X or Z
				if (direction == Direction.down) z++;
				else if (direction == Direction.left) x--;
				else if (direction == Direction.up) z--;
				else x++;
				
				this.openRegionCoordinates = this.nextRegionCoordinates;
				this.nextRegionCoordinates = new RegionCoordinates(x, z);
			}
		
			//after finishing a side, change directions
			if (direction == Direction.down) direction = Direction.left;
			else if (direction == Direction.left) direction = Direction.up;
			else if (direction == Direction.up) direction = Direction.right;
			else direction = Direction.down;
			
			//keep count of the completed sides
			side++;

			//on even-numbered sides starting with side == 2, increase the length of each side
			if (side % 2 == 0) sideLength++;
		}
		
		//return total number of regions seen
		return regionCount;
	}
	
	//picks a region at random (sort of)
	public RegionCoordinates getRandomRegion(RegionCoordinates regionToAvoid)
	{
		if(this.regionCount < 2) return null;
		
		//initialize random number generator with a seed based the current time
		Random randomGenerator = new Random();
		
		//get a list of all the files in the region data folder
		//some of them are named after region names, others region coordinates
		File regionDataFolder = new File(regionDataFolderPath);
		File [] files = regionDataFolder.listFiles();			
		ArrayList<RegionCoordinates> regions = new ArrayList<RegionCoordinates>();
		
		for(int i = 0; i < files.length; i++)
		{				
			if(files[i].isFile())  //avoid any folders
			{
				try
				{
					//if the filename converts to region coordinates, add that region to the list of defined regions
					//(this constructor throws an exception if it can't do the conversion)
					RegionCoordinates regionCoordinates = new RegionCoordinates(files[i].getName());
					if(!regionCoordinates.equals(regionToAvoid))
					{
						regions.add(regionCoordinates);
					}
				}
				
				//catch for files named after region names
				catch(Exception e){ }					
			}
		}
		
		//pick one of those regions at random
		int randomRegion = randomGenerator.nextInt(regions.size());			
		return regions.get(randomRegion);			
	}
	
	public void savePlayerData(OfflinePlayer player, PlayerData data)
	{
		//save that data in memory
		this.playerNameToPlayerDataMap.put(player.getName(), data);
		
		BufferedWriter outStream = null;
		try
		{
			//open the player's file
			File playerFile = new File(playerDataFolderPath + File.separator + player.getName());
			playerFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(playerFile));
			
			//first line is home region coordinates
			outStream.write(data.homeRegion.toString());
			outStream.newLine();
			
			//second line is last disconnection date,
			//note use of the ROOT locale to avoid problems related to regional settings on the server being updated
			DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.ROOT);			
			outStream.write(dateFormat.format(data.lastDisconnect));
			outStream.newLine();
			
			//third line is login priority
			outStream.write(String.valueOf(data.loginPriority));
			outStream.newLine();
		}		
		
		//if any problem, log it
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("PopulationDensity: Unexpected exception saving data for player \"" + player.getName() + "\": " + e.getMessage());
		}		
		
		try
		{
			//close the file
			if(outStream != null) outStream.close();
		}
		catch(IOException exception){}
	}
	
	public PlayerData getPlayerData(OfflinePlayer player)
	{
		//first, check the in-memory cache
		PlayerData data = this.playerNameToPlayerDataMap.get(player.getName());
		
		if(data != null) return data;
		
		//if not there, try to load the player from file		
		loadPlayerDataFromFile(player.getName());
		
		//check again
		data = this.playerNameToPlayerDataMap.get(player.getName());
		
		if(data != null) return data;
		
		return new PlayerData();
	}
	
	private void loadPlayerDataFromFile(String playerName)
	{
		//load player data into memory		
		File playerFile = new File(playerDataFolderPath + File.separator + playerName);
		
		BufferedReader inStream = null;
		try
		{					
			PlayerData playerData = new PlayerData();
			inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));
						
			//first line is home region coordinates
			String homeRegionCoordinatesString = inStream.readLine();
			
			//second line is date of last disconnection
			String lastDisconnectedString = inStream.readLine();
			
			//third line is login priority
			String rankString = inStream.readLine(); 
			
			//convert string representation of home coordinates to a proper object
			RegionCoordinates homeRegionCoordinates = new RegionCoordinates(homeRegionCoordinatesString);
			playerData.homeRegion = homeRegionCoordinates;
			  
			//parse the last disconnect date string
			try
			{
				DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.ROOT);
				Date lastDisconnect = dateFormat.parse(lastDisconnectedString);
				playerData.lastDisconnect = lastDisconnect;
			}
			catch(Exception e)
			{
				playerData.lastDisconnect = Calendar.getInstance().getTime();
			}
			
			//parse priority string
			if(rankString == null || rankString.isEmpty())
			{
				playerData.loginPriority = 0;
			}
			
			else
			{
				try
				{
					playerData.loginPriority = Integer.parseInt(rankString);
				}
				catch(Exception e)
				{
					playerData.loginPriority = 0;
				}			
			}
			  
			//shove into memory for quick access
			this.playerNameToPlayerDataMap.put(playerName, playerData);
		}
		
		//if the file isn't found, just don't do anything (probably a new-to-server player)
		catch(FileNotFoundException e) 
		{ 
			return;
		}
		
		//if there's any problem with the file's content, log an error message and skip it		
		catch(Exception e)
		{
			 PopulationDensity.AddLogEntry("Unable to load data for player \"" + playerName + "\": " + e.getMessage());			 
		}
		
		try
		{
			if(inStream != null) inStream.close();
		}
		catch(IOException exception){}		
	}
	
	//adds a new region, assigning it a name and updating local variables accordingly
	public RegionCoordinates addRegion()
	{
		//first, find a unique name for the new region
		String newRegionName; 
		
		//select a name from the list of region names		
		//strategy: use names from the list in rotation, appending a number when a name is already used
		//(redstone, mountain, valley, redstone1, mountain1, valley1, ...)
		
		int newRegionNumber = this.regionCount++ - 1;
		
		//as long as the generated name is already in use, move up one name on the list
		do
		{
			newRegionNumber++;
			int nameBodyIndex = newRegionNumber % this.regionNamesList.length;
			int nameSuffix = newRegionNumber / this.regionNamesList.length;
			newRegionName = this.regionNamesList[nameBodyIndex];
			if(nameSuffix > 0) newRegionName += nameSuffix;
			
		}while(this.getRegionCoordinates(newRegionName) != null);
		
		this.nameRegion(this.nextRegionCoordinates, newRegionName);		
		
		//find the next region in the spiral (updates this.openRegionCoordinates and this.nextRegionCoordinates)
		this.findNextRegion();
		
		return this.openRegionCoordinates;
	}
	
	void nameRegion(RegionCoordinates coords, String name) 
	{
		//region names are always lowercase
		name = name.toLowerCase();
		
		//delete any existing data for the region at these coordinates
		String oldRegionName = this.getRegionName(coords);
		if(oldRegionName != null)
		{
			File oldRegionCoordinatesFile = new File(regionDataFolderPath + File.separator + coords.toString());
			oldRegionCoordinatesFile.delete();
			
			File oldRegionNameFile = new File(regionDataFolderPath + File.separator + oldRegionName);
			oldRegionNameFile.delete();
		}

		//"create" the region by saving necessary data to disk
		//(region names to coordinates mappings aren't kept in memory because they're less often needed, and this way we keep it simple) 
		BufferedWriter outStream = null;
		try
		{
			//coordinates file contains the region's name
			File regionNameFile = new File(regionDataFolderPath + File.separator + name);
			regionNameFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(regionNameFile));
			outStream.write(coords.toString());
			outStream.close();
			
			//name file contains the coordinates
			File regionCoordinatesFile = new File(regionDataFolderPath + File.separator + coords.toString());
			regionCoordinatesFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(regionCoordinatesFile));
			outStream.write(name);
			outStream.close();			
		}
		
		//in case of any problem, log the details
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("Unexpected Exception: " + e.getMessage());
		}
		
		try
		{
			if(outStream != null) outStream.close();		
		}
		catch(IOException exception){}
	}

	//retrieves the open region's coordinates
	public RegionCoordinates getOpenRegion()
	{
		return this.openRegionCoordinates;
	}
	
	//goes to disk to get the name of a region, given its coordinates
	public String getRegionName(RegionCoordinates coordinates)
	{
		File regionCoordinatesFile;
		
		BufferedReader inStream = null;
		String regionName = null;
		try
		{
			regionCoordinatesFile = new File(regionDataFolderPath + File.separator + coordinates.toString());			
			inStream = new BufferedReader(new FileReader(regionCoordinatesFile));
			
			//only one line in the file, the region name
			regionName = inStream.readLine();
		}
		
		//if the file doesn't exist, the region hasn't been named yet, so return null
		catch(FileNotFoundException e)
		{			
			return null;
		}
		
		//if any other problems, log the details
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("Unable to read region data: " + e.getMessage());
			return null;
		}
		
		try
		{
			if(inStream != null) inStream.close();
		}
		catch(IOException exception){}
		
		return regionName;
	}
	
	//similar to above, goes to disk to get the coordinates that go with a region name
	public RegionCoordinates getRegionCoordinates(String regionName)
	{
		File regionNameFile = new File(regionDataFolderPath + File.separator + regionName);
		
		BufferedReader inStream = null;
		RegionCoordinates coordinates = null;
		try
		{
			inStream = new BufferedReader(new FileReader(regionNameFile));
			
			//only one line in the file, the coordinates
			String coordinatesString = inStream.readLine();
			
			inStream.close();			
			coordinates = new RegionCoordinates(coordinatesString);
		}
		
		//file not found means there's no region with a matching name, so return null
		catch(FileNotFoundException e) { }
		
		//if any other problems, log the details and then return null
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("Unable to read region data at " + regionNameFile.getAbsolutePath() + ": " + e.getMessage());			
		}
		
		try
		{
			if(inStream != null) inStream.close();
		}
		catch(IOException exception) {}
		
		return coordinates;
	}
	
	//actually edits the world to create a region post at the center of the specified region	
	public void AddRegionPost(RegionCoordinates region, boolean updateNeighboringRegions)
	{
		//if region post building is disabled, don't do anything
		if(!PopulationDensity.instance.buildRegionPosts) return;
		
		//find the center
		Location regionCenter = PopulationDensity.getRegionCenter(region);		
		int x = regionCenter.getBlockX();
		int z = regionCenter.getBlockZ();
		int y;

		//make sure data is loaded for that area, because we're about to request data about specific blocks there
		PopulationDensity.GuaranteeChunkLoaded(x, z);
		
		//sink lower until we find something solid
		//also ignore glowstone, in case there's already a post here!
		//race condition issue: chunks say they're loaded when they're not.  if it looks like the chunk isn't loaded, try again (up to five times)
		int retriesLeft = 5;
		boolean tryAgain;
		do
		{
			tryAgain = false;
			
			//find the highest block.  could be the surface, a tree, some grass...
			y = PopulationDensity.ManagedWorld.getHighestBlockYAt(x, z) + 1;
			
			//posts fall through trees, snow, and any existing post looking for the ground
			Material blockType;
			do
			{
				blockType = PopulationDensity.ManagedWorld.getBlockAt(x, --y, z).getType();
			}
			while(	y > 2 && (
					blockType == Material.AIR 		|| 
					blockType == Material.LEAVES 	|| 
			        blockType == Material.LEAVES_2  ||
					blockType == Material.LONG_GRASS||
					blockType == Material.LOG       ||
			        blockType == Material.LOG_2     ||
					blockType == Material.GLOWSTONE ||
					blockType == Material.SNOW 		||
					blockType == Material.VINE 		||					
					blockType == Material.SIGN_POST
					));
			
			//if final y value is extremely small, it's probably wrong
			if(y < 5 && retriesLeft-- > 0)
			{
				tryAgain = true;
				try
				{
					Thread.sleep(500); //sleep half a second before restarting the loop
				}
				catch(InterruptedException e) {}
			}
		}while(tryAgain);
				
		//if y value is under sea level, correct it to sea level (no posts should be that difficult to find)
		if(y < PopulationDensity.instance.minimumRegionPostY)
		{
			y = PopulationDensity.instance.minimumRegionPostY;
		}
		
		//clear signs from the area, this ensures signs don't drop as items 
		//when the blocks they're attached to are destroyed in the next step
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				for(int y1 = y + 1; y1 <= y + 15; y1++)
				{
					Block block = PopulationDensity.ManagedWorld.getBlockAt(x1, y1, z1);
					if(block.getType() == Material.SIGN_POST || block.getType() == Material.SIGN || block.getType() == Material.WALL_SIGN)
						block.setType(Material.AIR);					
				}
			}
		}
		
		//clear above it - sometimes this shears trees in half (doh!)
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				for(int y1 = y + 1; y1 < PopulationDensity.ManagedWorld.getMaxHeight(); y1++)
				{
					PopulationDensity.ManagedWorld.getBlockAt(x1, y1, z1).setType(Material.AIR);
				}
			}
		}	
		
		//build a glowpost in the center
		for(int y1 = y; y1 <= y + 3; y1++)
		{
			PopulationDensity.ManagedWorld.getBlockAt(x, y1, z).setType(Material.GLOWSTONE);
		}
		
		//build a stone platform
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				PopulationDensity.ManagedWorld.getBlockAt(x1, y, z1).setType(Material.SMOOTH_BRICK);
			}
		}
		
		//if the region has a name, build a sign on top
		String regionName = this.getRegionName(region);
		if(regionName != null)
		{		
			regionName = PopulationDensity.capitalize(regionName);
			Block block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 4, z);
			block.setType(Material.SIGN_POST);
			
			org.bukkit.block.Sign sign = (org.bukkit.block.Sign)block.getState();
			sign.setLine(1, PopulationDensity.capitalize(regionName));
			sign.setLine(2, "Region");
			sign.update();
		}
		
		//add a sign for the region to the south
		regionName = this.getRegionName(new RegionCoordinates(region.x + 1, region.z));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		Block block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 2, z - 1);
		
		org.bukkit.material.Sign signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.NORTH);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		org.bukkit.block.Sign sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
		sign.setLine(1, regionName);
	    sign.setLine(2, "Region");
		sign.setLine(3, "<--");
		
		sign.update();
		
		//if a city world is defined, also add a /cityregion sign on the east side of the post
		if(PopulationDensity.CityWorld != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z - 1);
			
			//signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			//signData.setFacingDirection(BlockFace.NORTH);
			
			//block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			//sign = (org.bukkit.block.Sign)block.getState();
			
			//sign.update();
		}
		
		//add a sign for the region to the east
		regionName = this.getRegionName(new RegionCoordinates(region.x, region.z - 1));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 2, z);
		
		signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.WEST);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
        sign.setLine(1, regionName);
        sign.setLine(2, "Region");
        sign.setLine(3, "<--");
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing north for teleportation help
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 3, z);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.WEST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			sign.setLine(0, "Teleport");
			sign.setLine(1, "From Here!");
			sign.setLine(2, "Punch For");
			sign.setLine(3, "Instructions");
			
			sign.update();
		}
		
		//add a sign for the region to the south
		regionName = this.getRegionName(new RegionCoordinates(region.x, region.z + 1));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 2, z);
		
		signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.EAST);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
        sign.setLine(1, regionName);
        sign.setLine(2, "Region");
        sign.setLine(3, "<--");
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing south for teleportation help
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 3, z);
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.EAST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			sign.setLine(0, "Teleport");
            sign.setLine(1, "From Here!");
            sign.setLine(2, "Punch For");
            sign.setLine(3, "Instructions");
			
			sign.update();
		}
		
		//add a sign for the region to the north
		regionName = this.getRegionName(new RegionCoordinates(region.x - 1, region.z));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 2, z + 1);
		
		signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.SOUTH);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
        sign.setLine(1, regionName);
        sign.setLine(2, "Region");
        sign.setLine(3, "<--");
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing west for /newestregion
		if(PopulationDensity.instance.allowTeleportation && !this.openRegionCoordinates.equals(region))
		{
			//block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z + 1);

			//signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			//signData.setFacingDirection(BlockFace.SOUTH);
			
			//block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);

			//sign = (org.bukkit.block.Sign)block.getState();
			
			//sign.update();
		}
		
		//custom signs
		
		if(PopulationDensity.instance.mainCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z - 1);

			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.NORTH);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.mainCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.northCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 1, z);

			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.WEST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.northCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.southCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 1, z);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.EAST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.southCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.eastCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 1, z - 1);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.NORTH);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.eastCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.westCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 1, z + 1);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.SOUTH);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.westCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(updateNeighboringRegions)
		{
			this.AddRegionPost(new RegionCoordinates(region.x - 1, region.z), false);
			this.AddRegionPost(new RegionCoordinates(region.x + 1, region.z), false);
			this.AddRegionPost(new RegionCoordinates(region.x, region.z - 1), false);
			this.AddRegionPost(new RegionCoordinates(region.x, region.z + 1), false);
		}
	}
	
	public void clearCachedPlayerData(Player player)
	{
		this.playerNameToPlayerDataMap.remove(player.getName());		
	}
	
	//list of region names to use
	private final String [] regionNamesList = new String [] 
	{
		"redstone",
		"morningthaw",
		"mountain",
		"valley",
		"wintersebb",
		"fjord",
		"ledge",
		"ravine",
		"darktide",
		"stream",
		"glenwood",
		"waterfall",
		"cragstone",
		"pickaxe",
		"axe",
		"hammer",
		"anvil",
		"field",
		"sunrise",
		"sunset",
		"copper",
		"coal",
		"shovel",
		"minecart",
		"railway",
		"tunnel",
		"chasm",
		"cavern",
		"ocean",
		"boat",
		"grass",
		"gust",
		"beach",
		"desert",
		"stone",
		"peak",
		"ore",
		"boulder",
		"hilltop",
		"horizon",
		"swamp",
		"molten",
		"canyon",
		"gravel",
		"sword",
		"obsidian",
		"treetop",
		"storm",
		"gold",
		"canopy",
		"forest",
		"summit",
		"glade",
		"trail",
		"seed",
		"diamond",
		"armor",
		"sand",
		"flint",
		"field",
		"steel",
		"helm",
		"gorge",
		"campfire",
		"workshop",
		"rubble",
		"iron",
		"torch",
		"moon",
		"shrub",
		"trunk",
		"garden",
		"vale",
		"pumpkin",
		"lantern",
		"charcoal",
		"marsh",
		"tundra",
		"taiga",
		"dust",
		"lava"
	};
}
