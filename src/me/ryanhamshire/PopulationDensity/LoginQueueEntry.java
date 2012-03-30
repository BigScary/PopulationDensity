package me.ryanhamshire.PopulationDensity;


public class LoginQueueEntry 
{
	public String playerName;
	int priority;
	long lastRefreshed;
	
	public LoginQueueEntry(String playerName, int priority, long lastRefreshed)
	{
		this.priority = priority;
		this.playerName = playerName;
		this.lastRefreshed = lastRefreshed;
	}
}
