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
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

public class DataStore 
{
	//in-memory cache for player home region, because it's needed very frequently
	//writes here also write immediately to file
	private HashMap<String, RegionCoordinates> playerNameToHomeRegionCoordinatesMap = new HashMap<String, RegionCoordinates>();
	
	//cache of last moved date, used infrequently
	//writes here also write immediately to file
	private HashMap<String, Date> playerNameToLastMovedDateMap = new HashMap<String, Date>();
	
	//whether or not a user has been warned about the semi-permanent nature of the /movein command
	//this isn't persisted across server sessions (lost on shutdown)
	private HashMap<String, Boolean> playerNameToMoveInWarnedMap = new HashMap<String, Boolean>();
	
	//which region each user was most recently invited to join
	//also not persisted across server sessions
	private HashMap<String, RegionCoordinates> playerNameToInvitationMap = new HashMap<String, RegionCoordinates>();
	
	//path information, for where stuff stored on disk is well...  stored
	private final static String dataLayerFolderPath = "plugins" + File.separator + "PopulationDensityData";
	private final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
	private final static String regionDataFolderPath = dataLayerFolderPath + File.separator + "RegionData";
	public final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
	
	//currently open region
	private RegionCoordinates openRegionCoordinates;
	
	//coordinates of the next region which will be opened, if one needs to be opened
	private RegionCoordinates nextRegionCoordinates;
	
	//initialization!
	public DataStore()
	{
		//ensure data folders exist
		new File(playerDataFolderPath).mkdirs();
		new File(regionDataFolderPath).mkdirs();
		
		//study region data and initialize both this.openRegionCoordinates and this.nextRegionCoordinates
		int regionCount = this.findNextRegion();
		PopulationDensity.AddLogEntry(regionCount + " total regions loaded.");
		
		//if no regions were loaded, create the first one
		if(regionCount == 0)
		{
			RegionCoordinates newRegion = this.addRegion();
			PopulationDensity.AddLogEntry("Created initial region \"" + this.getRegionName(newRegion) + "\" at " + newRegion.toString() + ".");
		}
		
		PopulationDensity.AddLogEntry("Open region: \"" + this.getRegionName(this.getOpenRegion()) + "\" at " + this.getOpenRegion().toString() + ".");		
	}
	
	//remembers an invitation
	public void setInvitation(String playerName, RegionCoordinates region)
	{
		this.playerNameToInvitationMap.put(playerName, region);
	}
	
	//recalls an invitation
	public RegionCoordinates getInvitation(Player player)
	{
		return playerNameToInvitationMap.get(player.getName());
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
	public RegionCoordinates getRandomRegion()
	{
		//initialize random number generator with a seed based the current time
		Random randomGenerator = new Random();
		
		//flip a coin (0-1)
		int randomNumber = randomGenerator.nextInt(2);
		
		//if heads, choose randomly from the home regions of online players
		//thinking: these are more likely to be populated than other regions
		if(randomNumber == 0)
		{
			//get a list of players
			Player [] players = PopulationDensity.instance.getServer().getOnlinePlayers();
			
			//pick one at random and return his home region
			int randomPlayer = randomGenerator.nextInt(players.length);			
			return this.getHomeRegionCoordinates(players[randomPlayer]);
		}
		
		//if tails, pick any region
		else
		{
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
						regions.add(regionCoordinates);
					}
					
					//catch for files named after region names
					catch(Exception e){ }					
				}
			}
			
			//pick one of those regions at random
			int randomRegion = randomGenerator.nextInt(regions.size());			
			return regions.get(randomRegion);			
		}
	}
	
	//notes that a specific player has been warned about the one week cooldown on changing home regions
	public void setWarnedAboutMoveIn(Player player)
	{
		this.playerNameToMoveInWarnedMap.put(player.getName(), new Boolean(true));
	}
	
	//retrieves the above data
	public boolean getWarnedAboutMoveIn(Player player)
	{
		return this.playerNameToMoveInWarnedMap.containsKey(player.getName());
	}	
		
	//sets a player's home region, both in memory and on disk
	public void setHomeRegionCoordinates(Player player, RegionCoordinates newHomeCoordinates)
	{
		//save to file
		this.savePlayer(player, newHomeCoordinates, this.playerNameToLastMovedDateMap.get(player.getName()));
	}
	
	//setse a player's last moved date, both in memory and on disk
	public void setLastMovedDate(Player player, Date newLastMovedDate)
	{
		this.savePlayer(player, this.playerNameToHomeRegionCoordinatesMap.get(player.getName()), newLastMovedDate);
	}
	
	//helper for above - does the work of actually writing to memory and files
	private void savePlayer(Player player, RegionCoordinates homeRegionCoordinates, Date lastMovedDate)
	{
		//if last moved date isn't available (this is the case for new players)
		if(lastMovedDate == null)
		{
			//set it to about 10 days ago.  this allows new players to move immediately after joining
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.DAY_OF_MONTH, -10);
			lastMovedDate = calendar.getTime();
		}
		
		//if home region isn't available (case for new players), make it the open region
		if(homeRegionCoordinates == null)
		{
			homeRegionCoordinates = this.getOpenRegion();
		}
		
		//save that data in memory
		this.playerNameToHomeRegionCoordinatesMap.put(player.getName(), homeRegionCoordinates);
		this.playerNameToLastMovedDateMap.put(player.getName(), lastMovedDate);
		
		try
		{
			//open the player's file
			File playerFile = new File(playerDataFolderPath + File.separator + player.getName());
			playerFile.createNewFile();
			BufferedWriter outStream = new BufferedWriter(new FileWriter(playerFile));
			
			//first line is home region coordinates
			outStream.write(homeRegionCoordinates.toString());
			outStream.newLine();
			
			//second line is last moved date,
			//note use of the ROOT locale to avoid problems related to regional settings on the server being updated
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.ROOT);			
			outStream.write(dateFormat.format(lastMovedDate));
			
			//close the file
			outStream.close();			
		}		
		
		//if any problem, log it
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("PopulationDensity: Unexpected exception saving data for player \"" + player.getName() + "\": " + e.getMessage());
		}
	}
	
	//home region coordinates are used mostly by block-related code to check whether or not the player is allowed to place or break a block
	public RegionCoordinates getHomeRegionCoordinates(Player player)
	{
		//first, check the in-memory cache
		RegionCoordinates region = this.playerNameToHomeRegionCoordinatesMap.get(player.getName());
		if(region != null) return region;
		
		//if not there, try to load the player from file		
		loadPlayerDataFromFile(player.getName());
		
		//check again (may return null, if there wasn't any player data to load from file)
		return this.playerNameToHomeRegionCoordinatesMap.get(player.getName());				
	}
	
	public Date getLastMovedDate(Player player)
	{
		//first, check the in-memory cache
		Date date = this.playerNameToLastMovedDateMap.get(player.getName());
		if(date != null) return date;
		
		//if not there, try to load the player from file		
		loadPlayerDataFromFile(player.getName());
		
		//check again (may return null, if there wasn't any player data to load from file)
		return this.playerNameToLastMovedDateMap.get(player.getName());
	}
	
	private void loadPlayerDataFromFile(String playerName)
	{
		//load player data into memory		
		File playerFile = new File(playerDataFolderPath + File.separator + playerName);
		
		try
		{					
			BufferedReader inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));
			
			//first line is home region coordinates
			String homeRegionCoordinatesString = inStream.readLine();
			
			//second line is date of last region move-in
			String lastMovedString = inStream.readLine();
			
			inStream.close();
			  
			//convert string representation of home coordinates to a proper object
			RegionCoordinates homeRegionCoordinates = new RegionCoordinates(homeRegionCoordinatesString);
			  
			//parse that date string
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.ROOT);
			Date lastMovedDate = dateFormat.parse(lastMovedString);
			  
			//shove both into memory for quick access
			this.playerNameToHomeRegionCoordinatesMap.put(playerName, homeRegionCoordinates);
			this.playerNameToLastMovedDateMap.put(playerName, lastMovedDate);
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
	}
	
	//adds a new region, assigning it a name and updating local variables accordingly
	public RegionCoordinates addRegion()
	{
		//first, find a unique name for the new region
		String newRegionName; 
		
		//if there are no regions yet, use the first name from the region names list
		if(this.openRegionCoordinates == null)
		{
			newRegionName = this.regionNamesList[0];
		}
		
		//otherwise, find a name that isn't already in use
		else
		{		
			//get the name of the currently open region
			String openRegionName = this.getRegionName(this.openRegionCoordinates);
					
			//if it exists, find it in the list of names
			int nameIndex = -1;		
			if(openRegionName != null)
			{
				for(nameIndex = 0; nameIndex < this.regionNamesList.length; nameIndex++)
				{
					if(this.regionNamesList[nameIndex].equalsIgnoreCase(openRegionName))
						break;
				}
			}
			
			//move up to next name in the list.  if at end, go back to start
			nameIndex = (nameIndex + 1) % this.regionNamesList.length;
				
			newRegionName = this.regionNamesList[nameIndex].toLowerCase();
		
			//if the name from the list is already in use...
			if(this.getRegionCoordinates(newRegionName) != null)
			{
				//append numbers until a name is created which isn't in use
				int numberToAppend = 0;
				String newRegionNameWithNumber;
				do
				{
					newRegionNameWithNumber = newRegionName + numberToAppend++;				
				}
				while(this.getRegionCoordinates(newRegionNameWithNumber) != null);
				
				newRegionName = newRegionNameWithNumber;
			}
		}

		//"create" the region by saving necessary data to disk
		//(region names to coordinates mappings aren't kept in memory because they're less often needed, and this way we keep it simple) 
		try
		{
			//coordinates file contains the region's name
			File regionNameFile = new File(regionDataFolderPath + File.separator + newRegionName);
			regionNameFile.createNewFile();
			BufferedWriter outStream = new BufferedWriter(new FileWriter(regionNameFile));
			outStream.write(this.nextRegionCoordinates.toString());
			outStream.close();
			
			//name file contains the coordinates
			File regionCoordinatesFile = new File(regionDataFolderPath + File.separator + this.nextRegionCoordinates.toString());
			regionCoordinatesFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(regionCoordinatesFile));
			outStream.write(newRegionName);
			outStream.close();		
		}
		
		//in case of any problem, log the details
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("Unexpected Exception: " + e.getMessage());
			return null;
		}
		
		//find the next region in the spiral (updates this.openRegionCoordinates and this.nextRegionCoordinates)
		this.findNextRegion();
		
		//build a signpost at the center of the newly opened region
		this.AddRegionPost(this.openRegionCoordinates, true);
		
		return this.openRegionCoordinates;
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
		
		try
		{
			regionCoordinatesFile = new File(regionDataFolderPath + File.separator + coordinates.toString());			
			BufferedReader inStream = new BufferedReader(new FileReader(regionCoordinatesFile));
			
			//only one line in the file, the region name
			String regionName = inStream.readLine();
			
			inStream.close();
			return regionName;
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
	}
	
	//similar to above, goes to disk to get the coordinates that go with a region name
	public RegionCoordinates getRegionCoordinates(String regionName)
	{
		File regionNameFile = new File(regionDataFolderPath + File.separator + regionName);
		
		try
		{
			BufferedReader inStream = new BufferedReader(new FileReader(regionNameFile));
			
			//only one line in the file, the coordinates
			String coordinatesString = inStream.readLine();
			
			inStream.close();			
			return new RegionCoordinates(coordinatesString);
		}
		
		//file not found means there's no region with a matching name, so return null
		catch(FileNotFoundException e)
		{
			return null;
		}
		
		//if any other problems, log the details
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("Unable to read region data at " + regionNameFile.getAbsolutePath() + ": " + e.getMessage());
			return null;
		}
	}
	
	//actually edits the world to create a region post at the center of the specified region	
	public void AddRegionPost(RegionCoordinates region, boolean updateNeighboringRegions)
	{
		//find the center
		Location regionCenter = PopulationDensity.getRegionCenter(region);		
		int x = regionCenter.getBlockX();
		int z = regionCenter.getBlockZ();
		int y;

		//make sure data is loaded for that area, because we're about to request data about specific blocks there
		PopulationDensity.GuaranteeChunkLoaded(x, z);
		
		//sink lower until we find something solid
		//also ignore glowstone, in case there's already a post here!
		//race condition issue: chunks say they're loaded when they're not.  if it looks like the chunk isn't loaded, try again (up to three times)
		int retriesLeft = 3;
		boolean tryAgain;
		do
		{
			tryAgain = false;
			
			//find the highest block.  could be the surface, a tree, some grass...
			y = PopulationDensity.ManagedWorld.getHighestBlockYAt(x, z) + 1;
			
			Material blockType;
			do
			{
				blockType = PopulationDensity.ManagedWorld.getBlockAt(x, --y, z).getType();
			}
			while(	y > 1 && (
					blockType == Material.AIR 		|| 
					blockType == Material.LEAVES 	|| 
					blockType == Material.GRASS		||
					blockType == Material.GLOWSTONE ||
					blockType == Material.SIGN_POST
					));
			
			//if final y value is extremely small, it's probably wrong
			if(y < 10 && retriesLeft-- > 0)
			{
				tryAgain = true;
				try
				{
					Thread.sleep(500); //sleep half a second before restarting the loop
				}
				catch(InterruptedException e) {}
			}
		}while(tryAgain);
				
		//build a stone platform
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				PopulationDensity.ManagedWorld.getBlockAt(x1, y + 1, z1).setType(Material.SMOOTH_BRICK);
			}
		}
		
		//clear above it - sometimes this shears trees in half (doh!)
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				for(int y1 = y + 2; y1 <= y + 15; y1++)
				{
					PopulationDensity.ManagedWorld.getBlockAt(x1, y1, z1).setType(Material.AIR);
				}
			}
		}
		
		//build a glowpost in the center
		for(int y1 = y + 1; y1 <= y + 4; y1++)
		{
			PopulationDensity.ManagedWorld.getBlockAt(x, y1, z).setType(Material.GLOWSTONE);
		}
		
		//if the region has a name, build a sign on top
		String regionName = this.getRegionName(region);
		if(regionName != null)
		{		
			regionName = PopulationDensity.capitalize(regionName);
			Block block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 5, z);
			block.setType(Material.SIGN_POST);
			
			Sign sign = (Sign)block.getState();
			sign.setLine(1, regionName);
			sign.update();
		}
		
		//add a sign for the region to the south
		regionName = this.getRegionName(new RegionCoordinates(region.x + 1, region.z));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		Block block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z - 1);
		block.setType(Material.WALL_SIGN);
		
		Sign sign = (Sign)block.getState();
		
		sign.setLine(0, "E");
		sign.setLine(1, "<--");
		sign.setLine(2, regionName);
		
		org.bukkit.material.Sign signData = (org.bukkit.material.Sign)sign.getData();
		signData.setFacingDirection(BlockFace.EAST);
		sign.setData(signData);		
		
		sign.update();
		
		//if a city world is defined, also add a /cityregion sign on the east side of the post
		if(PopulationDensity.CityWorld != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 4, z - 1);
			block.setType(Material.WALL_SIGN);
			
			sign = (Sign)block.getState();
			
			sign.setLine(0, "Visit the City:");
			sign.setLine(1, "/cityregion");
			sign.setLine(2, "Return Home:");
			sign.setLine(3, "/homeregion");
			
			signData = (org.bukkit.material.Sign)sign.getData();
			signData.setFacingDirection(BlockFace.EAST);
			sign.setData(signData);		
			
			sign.update();
		}
		
		//add a sign for the region to the east
		regionName = this.getRegionName(new RegionCoordinates(region.x, region.z - 1));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 3, z);
		block.setType(Material.WALL_SIGN);
		
		sign = (Sign)block.getState();
		
		sign.setLine(0, "N");
		sign.setLine(1, "<--");
		sign.setLine(2, regionName);
		
		signData = (org.bukkit.material.Sign)sign.getData();
		signData.setFacingDirection(BlockFace.NORTH);
		sign.setData(signData);		
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing north for /visitregion
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 4, z);
			block.setType(Material.WALL_SIGN);
			
			sign = (Sign)block.getState();
			
			sign.setLine(1, "Visit Friends:");
			sign.setLine(2, "/visitregion");
			
			signData = (org.bukkit.material.Sign)sign.getData();
			signData.setFacingDirection(BlockFace.NORTH);
			sign.setData(signData);		
			
			sign.update();
		}
		
		//add a sign for the region to the west
		regionName = this.getRegionName(new RegionCoordinates(region.x, region.z + 1));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 3, z);
		block.setType(Material.WALL_SIGN);
		
		sign = (Sign)block.getState();
		
		sign.setLine(0, "S");
		sign.setLine(1, "<--");
		sign.setLine(2, regionName);
		
		signData = (org.bukkit.material.Sign)sign.getData();
		signData.setFacingDirection(BlockFace.SOUTH);
		sign.setData(signData);		
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing south for /homeregion
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 4, z);
			block.setType(Material.WALL_SIGN);
			
			sign = (Sign)block.getState();
			
			sign.setLine(1, "Return Home:");
			sign.setLine(2, "/homeregion");
			
			signData = (org.bukkit.material.Sign)sign.getData();
			signData.setFacingDirection(BlockFace.SOUTH);
			sign.setData(signData);		
			
			sign.update();
		}
		
		//add a sign for the region to the north
		regionName = this.getRegionName(new RegionCoordinates(region.x - 1, region.z));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z + 1);
		block.setType(Material.WALL_SIGN);
		
		sign = (Sign)block.getState();
		
		sign.setLine(0, "W");
		sign.setLine(1, "<--");
		sign.setLine(2, regionName);
		
		signData = (org.bukkit.material.Sign)sign.getData();
		signData.setFacingDirection(BlockFace.WEST);
		sign.setData(signData);		
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing west for /newestregion and /randomregion
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 4, z + 1);
			block.setType(Material.WALL_SIGN);
			
			sign = (Sign)block.getState();
			
			sign.setLine(0, "Adventure!");
			sign.setLine(2, "/newestregion");
			sign.setLine(3, "/randomregion");
			
			signData = (org.bukkit.material.Sign)sign.getData();
			signData.setFacingDirection(BlockFace.WEST);
			sign.setData(signData);		
			
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
	
	//list of region names to use
	private final String [] regionNamesList = new String [] 
	{
		"redstone",
		"mountain",
		"valley",
		"fjord",
		"ledge",
		"ravine",
		"stream",
		"waterfall",
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
