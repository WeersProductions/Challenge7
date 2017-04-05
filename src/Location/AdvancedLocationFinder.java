package Location;

import Utils.MacRssiPair;
import Utils.Position;
import Utils.Utils;
import org.omg.CORBA.INTERNAL;

import javax.tools.JavaCompiler;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Simple Location finder that returns the first known APs location from the list of received MAC addresses
 * @author Bernd
 *
 */
public class AdvancedLocationFinder implements LocationFinder{

    private final int A = -30;
    private final float N =2f;

	private HashMap<String, Position> knownLocations; //Contains the known locations of APs. The long is a MAC address.

	public AdvancedLocationFinder(){
		knownLocations = Utils.getKnownLocations(); //Put the known locations in our hashMap
	}

	@Override
	public Position locate(MacRssiPair[] data) {
		//Sort the array.
		Arrays.sort(data, Comparator.comparingInt(o -> o.getRssi()));

		//reverse the array.
		MacRssiPair temp;
		for (int i = 0; i < data.length / 2; i++)
		{
			temp = data[i];
			data[i] = data[data.length - 1 - i];
			data[data.length - 1 - i] = temp;
		}

		//Data is now sorted based on the length.
		printMacs(data);

		//Where the value is the MilliWatt.
		Map<MacRssiPair, Double> macRssiPairMap= new HashMap<>();
        //Create a map with only the known locations.
        for (MacRssiPair pair : data) {
            if(knownLocations.containsKey(pair.getMacAsString())) {
                double milliWat = dbmToMilliWatt(pair.getRssi());
                macRssiPairMap.put(pair, milliWat);
            }
        }

        //Where the value is the distance.
        Map<MacRssiPair, Double> macRssiPairDistance = new HashMap<>();
        MacRssiPair closestPair = null;
        for(Map.Entry<MacRssiPair, Double> entry : macRssiPairMap.entrySet()) {
			macRssiPairDistance.put(entry.getKey(), Math.pow(10, (entry.getKey().getRssi() - A) / (-10 * N)));
			System.out.println(knownLocations.get(entry.getKey().getMacAsString()) + "    " +macRssiPairDistance.get(entry.getKey()));
			if(macRssiPairDistance.get(entry.getKey()) < (closestPair == null ? Integer.MAX_VALUE : macRssiPairDistance.get(closestPair))) {
                closestPair = entry.getKey();
			}
        }

        //We shift the closest one and set the position to 0,0.
		Position shift = new Position(-knownLocations.get(closestPair.getMacAsString()).getX(), -knownLocations.get(closestPair.getMacAsString()).getY());

		//A map with the shifted position.
        Map<MacRssiPair, Position> macRssiPairShiftedPosition = new HashMap<>();

        //Shift all positions.
        for(Map.Entry<MacRssiPair, Double> entry : macRssiPairDistance.entrySet()) {
            macRssiPairShiftedPosition.put(entry.getKey(), new Position(knownLocations.get(entry.getKey().getMacAsString()).getX() + shift.getX(), knownLocations.get(entry.getKey().getMacAsString()).getY() + shift.getY()));
		}

        double r1 = macRssiPairDistance.get(closestPair);
        double r2 = 0;
        double d = 0;
		for (Map.Entry<MacRssiPair, Position> entry : macRssiPairShiftedPosition.entrySet()) {
			if(entry.getValue().getX() == 0 ^ entry.getValue().getY() == 0) {
				r2 = macRssiPairDistance.get(entry.getKey());
				if(entry.getValue().getX() == 0) {
					d = entry.getValue().getY();
				} else {
					d = entry.getValue().getX();
				}
				break;
			}
		}

		double r3 = 0;
		double i = 0;
		double j = 0;
		for(Map.Entry<MacRssiPair, Position> entry : macRssiPairShiftedPosition.entrySet()) {
			if(entry.getValue().getX() != 0 && entry.getValue().getY() != 0) {
				r3 = macRssiPairDistance.get(entry.getKey());
				i = entry.getValue().getX();
				j = entry.getValue().getY();
				break;
			}
		}

		System.out.println("Shift: " + shift);
		System.out.println("r1: " + r1);
		System.out.println("r2: " + r2);
		System.out.println("r3: " + r3);
		System.out.println("d:  " + d);
		System.out.println("i:  " + i);
		System.out.println("j:  " + j);

		double x = (Math.pow(r1, 2) - Math.pow(r2, 2) + Math.pow(d, 2)) / (2 * d);
		double y = (Math.pow(r1, 2) - Math.pow(r3, 2) + Math.pow(i, 2) + Math.pow(j, 2)) / (2 * j) - (i/j) * x;
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		x -= shift.getX();
		y -= shift.getY();
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("---------------");

//        //Where the value is the relative distance.
//        Map<MacRssiPair, Double> macRssiPairRelativeDistance = new HashMap<>();
//        if(closest == null) {
//            System.out.println("NOT POSSIBLE");
//        }
//
//        //Create the map with relative distances.
//        for(Map.Entry<MacRssiPair, Double> entry : macRssiPairMap.entrySet()) {
//                macRssiPairRelativeDistance.put(entry.getKey(), Math.pow(2, Math.log(closestDistance) - Math.log(entry.getValue())));
//        }

		//System.out.println("--------");
		//return getFirstKnownFromList(data); //return the first known APs location
		return new Position(x,y);
	}
	
	/**
	 * Returns the position of the first known AP found in the list of MacRssi pairs
	 * @param data
	 * @return
	 */
	private Position getFirstKnownFromList(MacRssiPair[] data){
		Position ret = new Position(0,0);
		for(int i=0; i<data.length; i++){
			if(knownLocations.containsKey(data[i].getMacAsString())){
				ret = knownLocations.get(data[i].getMacAsString());
				break;
			}
		}
		return ret;
	}
	
	/**
	 * Outputs all the received MAC RSSI pairs to the standard out
	 * This method is provided so you can see the data you are getting
	 * @param data
	 */
	private void printMacs(MacRssiPair[] data) {
		for (MacRssiPair pair : data) {
		    if(knownLocations.containsKey(pair.getMacAsString())) {

                System.out.println(pair + "  " + knownLocations.get(pair.getMacAsString()));
            }

		}
	}

	private double dbmToMilliWatt(int rssi){
	    return Math.pow(10, rssi / (float)10);
    }
}
