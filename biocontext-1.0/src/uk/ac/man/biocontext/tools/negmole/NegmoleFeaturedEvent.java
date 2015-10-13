package uk.ac.man.biocontext.tools.negmole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import martin.common.Pair;

import uk.ac.man.biocontext.dataholders.BindingEvent;
import uk.ac.man.biocontext.dataholders.Event;
import uk.ac.man.biocontext.dataholders.Node;
import uk.ac.man.biocontext.dataholders.RegulationEvent;
import uk.ac.man.biocontext.dataholders.SimpleEvent;
import uk.ac.man.biocontext.dataholders.Exception.NodeNotInSentenceException;
import uk.ac.man.biocontext.tools.negmole.Features.Classes;
import uk.ac.man.biocontext.tools.negmole.Features.NegOrSpec;

public class NegmoleFeaturedEvent {	
	static int MAX_DIST = 1000;
	private Classes classs;
	private NegOrSpec negSpec;

	//private List<Integer> featureDims = new ArrayList<Integer>();
	//private List<Double> featureValues = new ArrayList<Double>();
	private Map<Integer, Integer> featureDims2Values = new HashMap<Integer, Integer>();

	//private boolean targetNegated;
	//private boolean targetSpeculated;
	private Event event;
	private Node sentence;

	List<Node> cues;
	boolean anyCuePresent;
	private Node trigger;

	private Node mainCue;

	public Event getEvent() {
		return event;
	}



	public NegmoleFeaturedEvent(Event event, NegOrSpec ns) throws NodeNotInSentenceException {
		if (SimpleEvent.EVENT_TYPES.contains(event.getEveType())){
			this.classs = Classes.I;
		} else if (BindingEvent.EVENT_TYPES.contains(event.getEveType())){
			this.classs = Classes.II;
		} else if (RegulationEvent.EVENT_TYPES.contains(event.getEveType())){
			this.classs = Classes.III;
		} else throw new IllegalStateException("unrecognised event type: " + event.getEveType());

		this.negSpec = ns;
		this.event = event;
		this.trigger = event.getTrigger();
		this.sentence = trigger.getRoot();

		if (ns.equals(NegOrSpec.N)){
			cues = getCueOccurances(Negation.NEGATION_CUES);
			
		}else{
			cues = getCueOccurances(Negation.SPECULATION_CUES);
		}
		
		if (! this.sentence.getLeaves().contains(this.trigger)){
			throw new NodeNotInSentenceException("Trigger is not in the sentence.\n" + sentence + "\n**and trigger is\n" + trigger);
		}
		if (cues.isEmpty()){
			this.mainCue = null;
		}else{
			this.mainCue = sentence.getClosest(trigger, cues);
		}

		this.setFeatureDimsAndValues();
	}



	public void setTargetNegated(boolean targetNegated) {
		this.getEvent().setNegated(targetNegated);
	}	

	public void setTargetSpeculated(boolean targetSpeculated) {
		this.getEvent().setSpeculated(targetSpeculated);
	}
	
	public void setTarget(boolean target, NegOrSpec ngsp){
		if (ngsp == NegOrSpec.N)
			setTargetNegated(target);
		else
			setTargetSpeculated(target);
	}

	private void setFeatureDimsAndValues_I_II_III_N_S(NegOrSpec ns) throws NodeNotInSentenceException{

		String trigTag = trigger.getTag();

		//TODO proper multiple cue handling
		if (negSpec.equals(NegOrSpec.N)){
			anyCuePresent = this.anyCuePresent(Negation.NEGATION_CUES);
		}else{
			anyCuePresent = this.anyCuePresent(Negation.SPECULATION_CUES);
		}
		


		//Populating the features vector
		//NOTE: any feature added here must also be added in Features and to the corresponding sets e.g. setAllN. 

		featureDims2Values.put(Features.lookupFeatureIndex("MCCLOSKY_TAGS.trig." + trigTag), 1);

		//TODO check why you get neg cue "not" for E1 in doc 8443122
		if (anyCuePresent){

			featureDims2Values.put(Features.lookupFeatureIndex("anyCuePresent"), 1);

			Node mainCue = sentence.getClosest(trigger, cues);

			boolean cueCommandsTrigger = sentence.commands(mainCue, trigger);
			if (cueCommandsTrigger){
				featureDims2Values.put(Features.lookupFeatureIndex("cueCommandsTrigger"), 1);
			}

			boolean cueVPCommandsTrigger = sentence.commands(mainCue, trigger, "VP");
			if (cueVPCommandsTrigger){
				featureDims2Values.put(Features.lookupFeatureIndex("cueVPCommandsTrigger"), 1);
			}
			
			boolean cueNPCommandsTrigger = sentence.commands(mainCue, trigger, "NP");
			if (cueNPCommandsTrigger){
				featureDims2Values.put(Features.lookupFeatureIndex("cueNPCommandsTrigger"), 1);
			}
			
			boolean cueJJCommandsTrigger = sentence.commands(mainCue, trigger, "JJ");
			if (cueJJCommandsTrigger){
				featureDims2Values.put(Features.lookupFeatureIndex("cueJJCommandsTrigger"), 1);
			}
			boolean cuePPCommandsTrigger = sentence.commands(mainCue, trigger, "PP");
			if (cuePPCommandsTrigger){
				featureDims2Values.put(Features.lookupFeatureIndex("cuePPCommandsTrigger"), 1);
			}
			
			
			String cueTag = mainCue.getTag();
			featureDims2Values.put(Features.lookupFeatureIndex("MCCLOSKY_TAGS.cue." + cueTag), 1);

			String commonParentTrigCueTag = sentence.lowestCommonAncestor(trigger, mainCue).getTag();
			featureDims2Values.put(Features.lookupFeatureIndex("MCCLOSKY_TAGS.commonParentTrigCue." + commonParentTrigCueTag), 1);

			int treeDistanceTrigCue = sentence.distance(trigger, mainCue);

			int surfaceDistanceTrigCue = sentence.surfaceDistance(trigger, mainCue);

			featureDims2Values.put(Features.lookupFeatureIndex("treeDistanceTrigCue"), treeDistanceTrigCue);

			featureDims2Values.put(Features.lookupFeatureIndex("surfaceDistanceTrigCue"), surfaceDistanceTrigCue);


			if (negSpec.equals(NegOrSpec.N)){
				featureDims2Values.put(Features.lookupFeatureIndex("NEGATION_CUES."+Stemmer.stemToken(mainCue.getData().get("text"))), 1);
			}else{
				featureDims2Values.put(Features.lookupFeatureIndex("SPECULATION_CUES."+Stemmer.stemToken(mainCue.getData().get("text"))), 1);
			}

		}else{
			if (negSpec.equals(NegOrSpec.N)){
				featureDims2Values.put(Features.lookupFeatureIndex("NEGATION_CUES."+""), 1);
			}else{
				featureDims2Values.put(Features.lookupFeatureIndex("SPECULATION_CUES."+""), 1);
			}
		}


	}

	private void setFeatureDimsAndValues_I() throws NodeNotInSentenceException{
		featureDims2Values.put(Features.lookupFeatureIndex("EVENT_TYPES."+event.getEveType()), 1);

		Node theme = event.getTheme();

		if (!sentence.contains(theme)){
			throw new NodeNotInSentenceException("");
		}
		
		String themeTag = theme.getTag();
		featureDims2Values.put(Features.lookupFeatureIndex("MCCLOSKY_TAGS.theme." + themeTag), 1);

//		System.out.println("***");
//		System.out.println(anyCuePresent);
//		System.out.println(mainCue != null);
		
		if (anyCuePresent){

//			System.out.println("\t***");
//			System.out.println("\t" + anyCuePresent);
//			System.out.println("\t" + mainCue.getData().get("text"));
			
			boolean cueCommandsTheme = sentence.commands(mainCue, theme);
//			System.out.println("Getting distance for " + theme.getID() + " and " + mainCue.getID() + " on sentence " + sentence.getID());
			int treeDistanceThemeCue = sentence.distance(theme, mainCue);
			int surfaceDistanceThemeCue = sentence.surfaceDistance(theme, mainCue);

			if (cueCommandsTheme){
				featureDims2Values.put(Features.lookupFeatureIndex("cueCommandsTheme"), 1);
			}
			featureDims2Values.put(Features.lookupFeatureIndex("treeDistanceThemeCue"), treeDistanceThemeCue);

			featureDims2Values.put(Features.lookupFeatureIndex("surfaceDistanceThemeCue"), surfaceDistanceThemeCue);
		}
	}

	private void setFeatureDimsAndValues_II() throws NodeNotInSentenceException{
		if (anyCuePresent && mainCue == null){
			throw new IllegalStateException("We really need to check for the main cue not to be null instead of anyCuePresent!");
		}

		BindingEvent e = (BindingEvent) event;

		for (Node theme : e.getThemes()){
			if (!sentence.contains(theme)){
				throw new NodeNotInSentenceException("");
			}
		}
		
		if(anyCuePresent){
			boolean cueCommandsAnyTheme = false;
			for (Node t : e.getThemes()){
				if (sentence.commands(mainCue, t)){
					cueCommandsAnyTheme = true;
					break;
				}
			}

			boolean cueCommandsAllThemes = true;
			for (Node t : e.getThemes()){
				if (!sentence.commands(mainCue, t)){
					cueCommandsAllThemes = false;
					break;
				}
			}

			Node closestTheme2Cue = sentence.getClosest(mainCue, e.getThemes());

			boolean cueCommandsClosestTheme2Cue = sentence.commands(mainCue, closestTheme2Cue);
			int surfaceDistanceClosestTheme2Cue = sentence.surfaceDistance(closestTheme2Cue, mainCue);
			int treeDistanceClosestTheme2Cue = sentence.distance(closestTheme2Cue, mainCue);

			featureDims2Values.put(Features.lookupFeatureIndex("cueCommandsAnyTheme"), cueCommandsAnyTheme ? 1 : 0);
			featureDims2Values.put(Features.lookupFeatureIndex("cueCommandsAllThemes"), cueCommandsAllThemes? 1 : 0);
			featureDims2Values.put(Features.lookupFeatureIndex("cueCommandsClosestTheme2Cue"), cueCommandsClosestTheme2Cue ? 1 : 0);
			featureDims2Values.put(Features.lookupFeatureIndex("surfaceDistanceClosestTheme2Cue"), surfaceDistanceClosestTheme2Cue);
			featureDims2Values.put(Features.lookupFeatureIndex("treeDistanceClosestTheme2Cue"), treeDistanceClosestTheme2Cue);
		}
		featureDims2Values.put(Features.lookupFeatureIndex("numberOfThemes"), e.getThemes().size());

	}

	private void setFeatureDimsAndValues_III() throws NodeNotInSentenceException{
		featureDims2Values.put(Features.lookupFeatureIndex("EVENT_TYPES."+event.getEveType()), 1);
		Node theme = event.getTheme();

		if (!sentence.contains(theme)){
			throw new NodeNotInSentenceException("");
		}
		
		String themeTag = theme.getTag();
		featureDims2Values.put(Features.lookupFeatureIndex("MCCLOSKY_TAGS.theme." + themeTag), 1);

		if (anyCuePresent){

			boolean cueCommandsTheme = sentence.commands(mainCue, theme);
			if (cueCommandsTheme){
				featureDims2Values.put(Features.lookupFeatureIndex("cueCommandsTheme"), 1);
			}

			
			int treeDistanceThemeCue = sentence.distance(theme, mainCue);
			featureDims2Values.put(Features.lookupFeatureIndex("treeDistanceThemeCue"), treeDistanceThemeCue);			
		}
		
		
		int surfaceDistanceThemeCue = sentence.surfaceDistance(theme, mainCue);
		featureDims2Values.put(Features.lookupFeatureIndex("surfaceDistanceThemeCue"), surfaceDistanceThemeCue);
		
		RegulationEvent re = (RegulationEvent) event;

		if (re.getThemeType().equals("T")){
			featureDims2Values.put(Features.lookupFeatureIndex("themeType"), 1);
		}else if (re.getThemeType().equals("E")){
			featureDims2Values.put(Features.lookupFeatureIndex("themeType"), 2);
			/*
			 * This won't work until the above TODO re recursive theme event building is written.
			 * 
			Event actualTheme = (Event) re.getActualTheme();
			if (actualTheme.getClass().equals(Classes.I)){
				featureDims2Values.put(Features.lookupFeatureIndex("themeTypeEI"), 1);
			}
			if (actualTheme.getClass().equals(Classes.II)){
				featureDims2Values.put(Features.lookupFeatureIndex("themeTypeEII"), 1);
			}
			if (actualTheme.getClass().equals(Classes.III)){
				featureDims2Values.put(Features.lookupFeatureIndex("themeTypeEIII"), 1);
			}*/
		}
		
		
		if (re.hasCause()){
			featureDims2Values.put(Features.lookupFeatureIndex("hasCause"), 1);
			featureDims2Values.put(Features.lookupFeatureIndex("causeType"), re.getCauseType().equals("T") ? 1 : 2);
		}
	}



	private void setFeatureDimsAndValues_N(){

	}

	private void setFeatureDimsAndValues_S(){

	}


	private void setFeatureDimsAndValues() throws NodeNotInSentenceException{

		setFeatureDimsAndValues_I_II_III_N_S(negSpec);

		if (negSpec == NegOrSpec.N){
			setFeatureDimsAndValues_N();
		} else {
			setFeatureDimsAndValues_S();
		}

		if (classs == Classes.I)
			setFeatureDimsAndValues_I();
		else if (classs == Classes.II)
			setFeatureDimsAndValues_II();
		else if (classs == Classes.III)
			setFeatureDimsAndValues_III();
	}

	
	public Map<Integer, Integer> getFeatureDims2Values() {
		return featureDims2Values;
	}

	public boolean isTargetNegated() {
		return this.getEvent().getNegated();
	}

	public boolean isTargetSpeculated() {
		return this.getEvent().getSpeculated();
	}

	private List<Node> getCueOccurances(String[] cueList){
		List<Node> res = new ArrayList<Node>();

		for (String nc : cueList){
			
			for(int i = 0; i < sentence.getLeaves().size(); i++){
				Node w = sentence.getLeaves().get(i);
				if (nc.equalsIgnoreCase(Stemmer.stemToken(w.getData().get("text")))){
					//this line sets the negation cue in the node. It is a separate functionality and shouldn't be here. 
					//it's never used anyway.
					w.getData().put(negSpec.toString() + "_cue", "true");
					if (i+1 == sentence.getLeaves().size() ||
							!(w.getData().get("text").equalsIgnoreCase("not") && 
							sentence.getLeaves().get(i+1).getData().get("text").equalsIgnoreCase("only"))){
						res.add(w);						
					}
					
				}
			}
		}
		return res;
	}
	private boolean anyCuePresent(String[] cueList){
		if (getCueOccurances(cueList).size() > 0){
			return true;
		}else{
			return false;
		}
	}

	private static NegmoleFeaturedEvent event2NegmoleFeaturedEvent(Event e, NegOrSpec ns) throws NodeNotInSentenceException{
		if (e == null){
			throw new IllegalStateException("encountered a null event");
		} else{
			NegmoleFeaturedEvent res = new NegmoleFeaturedEvent(e, ns);
			if (res == null){
				throw new IllegalStateException("encountered a null featured-event");
			} else{
				return res;
			}
		}
	}

	public static List<NegmoleFeaturedEvent> getNegmoleFeaturedEventList(List<Event> events, NegOrSpec ns){
		List<NegmoleFeaturedEvent> negmoleFeaturedEvents = new ArrayList<NegmoleFeaturedEvent>(events.size());
		for (Event e : events){
			try {
				negmoleFeaturedEvents.add(event2NegmoleFeaturedEvent(e, ns));
			} catch (NodeNotInSentenceException e1) {
//				System.out.println("Woha! " + e.toString() + ", ns: " + ns.toString());
//				e1.printStackTrace();
				continue;
			}
		}
		return negmoleFeaturedEvents;
	}

	public Node getMainCue() {
		return mainCue;
	}
	
	public Pair<Integer> getMainCueOffset(String docText){
		if (mainCue == null)
			return null;
		
		String cueText = mainCue.getData().get("text");
		List<Pair<Integer>> allCues= new ArrayList<Pair<Integer>>();
		
		int st = docText.indexOf(cueText);
		while (st != -1){
			allCues.add(new Pair<Integer>(st, st+cueText.length()));
			st = docText.indexOf(cueText, st+1);
		}
		
		for(Pair<Integer> cueX : allCues){
			Node forest = sentence.getParent();
			if (forest.pair2Node(docText, cueX) == mainCue){
				mainCue.getData().put("offset", cueText.toString());
				return cueX;
			}
		}
		return null;
	}
	
	public String toString(){
		String res = this.event.toString();
		if (this.negSpec == NegOrSpec.N){
			res += "Negated:\t" + this.isTargetNegated();
		}else{
			res += "Speculated:\t" + this.isTargetSpeculated();
		}
		return res;
	}
}
