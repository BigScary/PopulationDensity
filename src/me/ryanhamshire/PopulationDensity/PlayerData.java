package me.ryanhamshire.PopulationDensity;
import java.util.Calendar;
import java.util.Date;

import org.bukkit.Location;

public class PlayerData 
{
	public RegionCoordinates homeRegion = null;	
	public RegionCoordinates regionInvitation = null;
	
	//afk-related variables
	public Location lastObservedLocation = null;
	public boolean hasTakenActionThisRound = true;
	public boolean wasInMinecartLastRound = false;
	public int afkCheckTaskID = -1;
	public int loginPriority = 0;
	public boolean advertisedMoveInThisSession = false;
	
	//queue-related variables
	public Date lastDisconnect;
	
	//initialization (includes some new player defaults)
	public PlayerData()
	{
		Calendar yesterday = Calendar.getInstance();
		yesterday.add(Calendar.DAY_OF_MONTH, -1);
		this.lastDisconnect = yesterday.getTime();
	}
}