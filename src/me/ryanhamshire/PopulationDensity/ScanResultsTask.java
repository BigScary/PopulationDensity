package me.ryanhamshire.PopulationDensity;

import java.util.ArrayList;

public class ScanResultsTask implements Runnable 
{
	private ArrayList<String> logEntries;
	private boolean openNewRegion;
	
	public ScanResultsTask(ArrayList<String> logEntries, boolean openNewRegion)
	{
		
		this.logEntries = logEntries;
		this.openNewRegion = openNewRegion;
	}
	
	@Override
	public void run() 
	{
		StringBuilder builder = new StringBuilder();
	    for(String entry : this.logEntries)
		{
			builder.append(entry).append("\n");
		}
	    
	    PopulationDensity.AddLogEntry(builder.toString());
		
		if(this.openNewRegion)
		{
			RegionCoordinates newRegion = PopulationDensity.instance.dataStore.addRegion();
			PopulationDensity.instance.scanRegion(newRegion, true);
		}		
	}
}
