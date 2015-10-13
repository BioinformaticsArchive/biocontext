package uk.ac.man.biocontext.tools.negmole;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import uk.ac.man.biocontext.dataholders.Event;
import uk.ac.man.biocontext.dataholders.RegulationEvent;
import uk.ac.man.biocontext.dataholders.SimpleEvent;

public class Features {
	public static enum Classes {I, II, III}
	public static enum NegOrSpec {N, S}
	
	static Map<String, Integer> allPossibleValues = new HashMap<String, Integer>();
	public static Map<Integer,String> allPossibleValuesReverse = new HashMap<Integer,String>();
	
	public static Map<Integer, Set<Integer>>  categoryDims;

//	public Map<Tuple<Classes, NegOrSpec>, Set<Integer>> getCategoryDims(){
//		return categoryDims;
//	}

	static {
		categoryDims = new HashMap<Integer, Set<Integer>>();
		
		Integer catNI = Classes.I.hashCode() + NegOrSpec.N.hashCode(); 
		Integer catNII = Classes.II.hashCode() + NegOrSpec.N.hashCode();
		Integer catNIII = Classes.III.hashCode() + NegOrSpec.N.hashCode();
		Integer catSI = Classes.I.hashCode() + NegOrSpec.S.hashCode();
		Integer catSII = Classes.II.hashCode() + NegOrSpec.S.hashCode();
		Integer catSIII = Classes.III.hashCode() + NegOrSpec.S.hashCode();
		
		Set<Integer> setNI = new TreeSet<Integer>();
		Set<Integer> setNII = new TreeSet<Integer>();
		Set<Integer> setNIII = new TreeSet<Integer>();
		Set<Integer> setSI = new TreeSet<Integer>();
		Set<Integer> setSII = new TreeSet<Integer>();
		Set<Integer> setSIII = new TreeSet<Integer>();
		
		Set<Integer> setAll = new TreeSet<Integer>();
		Set<Integer> setAllN = new TreeSet<Integer>();
		Set<Integer> setAllS = new TreeSet<Integer>();
		
		int i = 1;
		//data of the form <"NEGATION_CUES.no", 0>, <"NEGATION_CUES.not", 1>, etc.
		
		for (String s : Event.EVENT_TYPES){
			if(SimpleEvent.EVENT_TYPES.contains(s)){
				setNI.add(i);
				setSI.add(i);
			} else if(RegulationEvent.EVENT_TYPES.contains(s)){
				setNIII.add(i);
				setSIII.add(i);
			}
			allPossibleValues.put("EVENT_TYPES."+ s, i++);
			//Binding is not needed, as there is only one type in class II binding
		}
		
		for (String s : Negation.NEGATION_CUES){
			setAllN.add(i);
			allPossibleValues.put("NEGATION_CUES."+ s, i++);
		}
		
		for (String s : Negation.SPECULATION_CUES){
			setAllS.add(i);
			allPossibleValues.put("SPECULATION_CUES."+ s, i++);
		}
		
		for (String s : Negation.MCCLOSKY_TAGS){
			setNI.add(i);
			setNII.add(i);
			setNIII.add(i);
			setSI.add(i);
			setSII.add(i);
			setSIII.add(i);
			allPossibleValues.put("MCCLOSKY_TAGS.trig."+ s, i++);
		}
		
		for (String s : Negation.MCCLOSKY_TAGS){
			setNI.add(i);
			setNII.add(i);
			setNIII.add(i);
			setSI.add(i);
			setSII.add(i);
			setSIII.add(i);
			allPossibleValues.put("MCCLOSKY_TAGS.cue."+ s, i++);
		}
		
		for (String s : Negation.MCCLOSKY_TAGS){
			setNI.add(i);
			setNII.add(i);
			setNIII.add(i);
			setSI.add(i);
			setSII.add(i);
			setSIII.add(i);
			allPossibleValues.put("MCCLOSKY_TAGS.commonParentTrigCue."+ s, i++);
		}
		
		for (String s : Negation.MCCLOSKY_TAGS){
			setNI.add(i);
			setSI.add(i);
//			setAll.add(i);
			//TODO ???
			allPossibleValues.put("MCCLOSKY_TAGS.theme."+ s, i++);
		}

		setAll.add(i);
		allPossibleValues.put("anyCuePresent", i++);

		
		setNI.add(i);
		setNII.add(i);
		setNIII.add(i);
		setSI.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("cueCommandsTrigger", i++);
		

//		setAll.add(i);
		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i);	
		setSI.add(i);
		setSIII.add(i);
		allPossibleValues.put("cueVPCommandsTrigger", i++);
		
		
//		setAll.add(i);
		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i);
		//setAllN.add(i);
//		setSI.add(i);
		setSIII.add(i);
		allPossibleValues.put("cueNPCommandsTrigger", i++);

		
//		setAll.add(i);
		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i);
		//setAllN.add(i);
//		setSI.add(i);
//		setSIII.add(i);
		allPossibleValues.put("cueJJCommandsTrigger", i++);

		
//		setAll.add(i);
		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i);
		//setAllN.add(i);
//		setSI.add(i);
//		setSIII.add(i);
		allPossibleValues.put("cuePPCommandsTrigger", i++);

		
		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i);
		setSI.add(i);
		//setSIII.add(i);
		allPossibleValues.put("cueCommandsTheme", i++);
		
		
		setNII.add(i);
		setNIII.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("cueCommandsAnyTheme", i++);
		
		
		setNII.add(i);
		setNIII.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("cueCommandsAllThemes", i++);
		
		
		setNII.add(i);
		setNIII.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("cueCommandsClosestTheme2Cue", i++);
		
		
		//setSIII.add(i);
		//setSIII.add(i);
		allPossibleValues.put("cueCommandsCause", i++);
		
		
		setNI.add(i);
		setNII.add(i);
		setNIII.add(i);
		setSI.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("treeDistanceTrigCue", i++);
		
		
		setNI.add(i);
		setNII.add(i);
		setNIII.add(i);
		setSI.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("surfaceDistanceTrigCue", i++);
		

		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i); DO NOT ENABLE
		setSI.add(i);
//		setSII.add(i);
		//setSIII.add(i); DO NOT ENABLE
		allPossibleValues.put("treeDistanceThemeCue", i++);
		
		
		setNI.add(i);
//		setNII.add(i);
//		setNIII.add(i); DO NOT ENABLE
		setSI.add(i);
		//setSIII.add(i); DO NOT ENABLE
		allPossibleValues.put("surfaceDistanceThemeCue", i++);
		
		setNII.add(i);
		setNIII.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("treeDistanceClosestTheme2Cue", i++);
		
		setNII.add(i);
		setNIII.add(i);
		setSII.add(i);
		setSIII.add(i);
		allPossibleValues.put("surfaceDistanceClosestTheme2Cue", i++);
		
		
//		setAll.add(i);
		//setNIII.add(i);
		//setSIII.add(i);
		allPossibleValues.put("themeType", i++);
		
		
		//setNIII.add(i);
		//setSIII.add(i);
		allPossibleValues.put("hasCause", i++);
		
		//setNIII.add(i);
		//setSIII.add(i);
		allPossibleValues.put("causeType", i++);
		
//		setNII.add(i);
//		setSII.add(i);
//		setAll.add(i);
		allPossibleValues.put("numberOfThemes", i++);
		
		
		Set<String> keys = new HashSet<String>();
		keys.addAll(allPossibleValues.keySet());
		for (String k : keys){
			if (!k.equals(k.toLowerCase()))
				allPossibleValues.put(k.toLowerCase(), allPossibleValues.get(k));
			if (allPossibleValuesReverse.containsKey(allPossibleValues.get(k)))
				throw new IllegalStateException("allPossibleValues contains multiple keys with the same value.\n" 
						+ k + " --- " + allPossibleValuesReverse.get(allPossibleValues.get(k)));
			allPossibleValuesReverse.put(allPossibleValues.get(k), k);
		}

		
		setNI.addAll(setAll);
		setNII.addAll(setAll);
		setNIII.addAll(setAll);
		setSI.addAll(setAll);
		setSII.addAll(setAll);
		setSIII.addAll(setAll);
		
		setNI.addAll(setAllN);
		setNII.addAll(setAllN);
		setNIII.addAll(setAllN);
		
		setSI.addAll(setAllS);
		setSII.addAll(setAllS);
		setSIII.addAll(setAllS);
		
		categoryDims.put(catNI, setNI);
		categoryDims.put(catNII, setNII);
		categoryDims.put(catNIII, setNIII);
		categoryDims.put(catSI, setSI);
		categoryDims.put(catSII, setSII);
		categoryDims.put(catSIII, setSIII);
	}
	
	public static int lookupFeatureIndex(String label){
		if (!Features.allPossibleValues.containsKey(label) && Features.allPossibleValues.containsKey(label.toLowerCase()))
			label = label.toLowerCase();

		if (!Features.allPossibleValues.containsKey(label))
			throw new IllegalStateException("label not found in all possible values: " + label);

		return Features.allPossibleValues.get(label);
	}
}
