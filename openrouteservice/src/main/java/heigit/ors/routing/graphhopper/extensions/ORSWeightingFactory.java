/*|----------------------------------------------------------------------------------------------
 *|														Heidelberg University
 *|	  _____ _____  _____      _                     	Department of Geography		
 *|	 / ____|_   _|/ ____|    (_)                    	Chair of GIScience
 *|	| |  __  | | | (___   ___ _  ___ _ __   ___ ___ 	(C) 2014-2017
 *|	| | |_ | | |  \___ \ / __| |/ _ \ '_ \ / __/ _ \	
 *|	| |__| |_| |_ ____) | (__| |  __/ | | | (_|  __/	Berliner Strasse 48								
 *|	 \_____|_____|_____/ \___|_|\___|_| |_|\___\___|	D-69120 Heidelberg, Germany	
 *|	        	                                       	http://www.giscience.uni-hd.de
 *|								
 *|----------------------------------------------------------------------------------------------*/
package heigit.ors.routing.graphhopper.extensions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import heigit.ors.routing.graphhopper.extensions.weighting.*;
import heigit.ors.routing.traffic.RealTrafficDataProvider;

import com.graphhopper.routing.weighting.DefaultWeightingFactory;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.storage.index.LocationIndex;

public class ORSWeightingFactory extends DefaultWeightingFactory {

	private RealTrafficDataProvider m_trafficDataProvider;
	private Map<Object, TurnCostExtension> m_turnCostExtensions;
	
	public ORSWeightingFactory(RealTrafficDataProvider trafficProvider)
	{
		m_trafficDataProvider = trafficProvider;
		m_turnCostExtensions = new HashMap<Object, TurnCostExtension>();
	}
	
	public Weighting createWeighting(HintsMap hintsMap, FlagEncoder encoder, Graph graph, LocationIndex locationIndex, Object userState) {
	    String weighting = hintsMap.getWeighting().toLowerCase();
		// System.out.println("ORSWeightingFactory.createWeighting(), weighting="+weighting);
		    
		Weighting result = null;
		
		GraphHopperStorage graphStorage = null;
		if (userState instanceof GraphHopperStorage)
		{
			graphStorage = (GraphHopperStorage) userState;
		}
		
        if ("shortest".equalsIgnoreCase(weighting))
        {
            result = new ShortestWeighting(encoder);
        } else //if ("fastest".equalsIgnoreCase(weighting))
        {
            if (encoder.supports(PriorityWeighting.class))
            {
            	if ("recommended_pref".equalsIgnoreCase(weighting))
                    result = new PreferencePriorityWeighting(encoder, hintsMap);
            	else if ("recommended".equalsIgnoreCase(weighting))
                    result = new OptimizedPriorityWeighting(encoder, hintsMap);
            	else
            		result = new FastestSafeWeighting(encoder, hintsMap);
            }
            else
                result = new FastestWeighting(encoder, hintsMap);
        } 
		
		if (hintsMap.getBool("SteepnessDifficulty", false)) {
			 int difficultyLevel = hintsMap.getInt("SteepnessDifficultyLevel", -1);
			 double maxSteepness = hintsMap.getDouble("SteepnessMaximum", -1);
			 result = new SteepnessDifficultyWeighting(result, encoder, hintsMap, graphStorage, difficultyLevel, maxSteepness);
	    }
		else if (hintsMap.getBool("AvoidHills", false)) {
			 double maxSteepness = hintsMap.getDouble("SteepnessMaximum", -1);
			 result = new AvoidHillsWeighting(result, encoder, hintsMap, (GraphStorage)userState, maxSteepness);
		}
		
		if (hintsMap.getBool("TrafficBlockWeighting", false))
		{
			//String strPref = weighting.substring(weighting.indexOf("-") + 1);
			result = new TrafficAvoidWeighting(result, encoder, m_trafficDataProvider.getAvoidEdges(graphStorage));
		}

		if (hintsMap.getBool("GreenRouting", false)) {
			result = new GreenWeighting(result, encoder, hintsMap, graphStorage);
		}

		if (encoder.supports(TurnWeighting.class) && !(encoder instanceof FootFlagEncoder) && graphStorage != null) {
			Path path = Paths.get(graphStorage.getDirectory().getLocation(), "turn_costs");
			File file = path.toFile();
			if (file.exists()) {
				TurnCostExtension turnCostExt = null;
				synchronized (m_turnCostExtensions) {
					turnCostExt = m_turnCostExtensions.get(graphStorage);
					if (turnCostExt == null) {
						turnCostExt = new TurnCostExtension();
						turnCostExt.init(graphStorage, graphStorage.getDirectory());
						m_turnCostExtensions.put(graphStorage, turnCostExt);
					}
				}

				result = new TurnWeighting(result, turnCostExt);
			}
		}

		if (result != null)
			return result;

		return super.createWeighting(hintsMap, encoder, graph, locationIndex, userState);
	}
	
	private Weighting createWeighting(Weighting w1, Weighting seq) {
		if (seq != null && seq instanceof WeightingSequence) {
			WeightingSequence seqWeighting = (WeightingSequence) seq;
			seqWeighting.addWeighting(w1);
			return seqWeighting;
		} else {
			ArrayList<Weighting> list = new ArrayList<Weighting>();
			list.add(w1);
			if (seq != null)
				list.add(seq);
			WeightingSequence seqWeighting = new WeightingSequence(list);
			return seqWeighting;
		}
	}
}
