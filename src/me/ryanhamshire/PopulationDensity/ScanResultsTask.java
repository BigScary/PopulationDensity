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
		//collect garbage
		System.gc();
		
		for(int i = 0; i < logEntries.size(); i++)
		{
			PopulationDensity.AddLogEntry(logEntries.get(i));
		}
		
		if(this.openNewRegion)
		{
			RegionCoordinates newRegion = PopulationDensity.instance.dataStore.addRegion();
			PopulationDensity.instance.scanRegion(newRegion, true);
		}		
	}
}
