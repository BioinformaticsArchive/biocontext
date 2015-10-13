package uk.ac.man.biocontext.dataholders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import uk.ac.man.textpipe.TextPipe;

import martin.common.Pair;

public abstract class Event extends Participant {
	public static Set<String> EVENT_TYPES = new TreeSet<String>(Arrays.asList(new String[] {"Transcription", "Localization", "Protein_catabolism", "Gene_expression", "Phosphorylation", "Binding", "Regulation", "Positive_regulation", "Negative_regulation"}));
	private String id;
	private String eveType;
	private String trigText;
	private Pair<Integer> trigIndex;
	private Node trigger;
	private String docId;
	
	public Pair<Integer> getTrigIndex() {
		return trigIndex;
	}
	public void setTrigIndex(Pair<Integer> trigIndex) {
		this.trigIndex = trigIndex;
	}
	public void setTrigIndex(int begin, int end) {
		this.trigIndex = new Pair<Integer>(begin, end);
	}
	//List<Token> participants;
	private boolean negated;
	private boolean speculated;
	
	
	public Event(String docId, String id, String eveType, String trigText,
			Pair<Integer> trigIndex, Node trigger) {
		this.docId = docId;
		this.id = id;
		this.eveType = eveType;
		this.trigText = trigText;
		this.trigIndex = trigIndex;
		this.negated = false;
		this.trigger = trigger;
	}

	public String getDocId() {
		return docId;
	}
	
	abstract public Node getTheme();
	
	public void setEveType(String eveType) {
		if (EVENT_TYPES.contains(eveType)){
			this.eveType = eveType;
		}
		else{
			System.exit(1);
		}
	}
	public String getEveType() {
		return eveType;
	}
	public void setId(String name) {
		this.id = name;
	}
	public String getId() {
		return id;
	}
	public void setTrigText(String trigger) {
		this.trigText = trigger;
	}
	public String getTrigText() {
		return trigText;
	}
	public void setNegated(boolean negated) {
		this.negated = negated;
	}
	public boolean getNegated() {
		return negated;
	}
	public void setSpeculated(boolean speculated) {
		this.speculated = speculated;
	}
	public boolean getSpeculated(){
		return this.speculated;
	}
	public void setTrigger(Node trigger) {
		this.trigger = trigger;
	}
	public Node getTrigger() {
		return trigger;
	}
	
	public String toString(){
		String res =  "Name:\t" + this.id + "\tType:\t" + this.eveType + "\t";
		
		return res;
		 
	}
	public Map<String, String> toMap(){
		Map<String, String> res = new HashMap<String, String>();
		res.put("id", this.id);
		res.put("type", this.eveType);
		res.put("negated", ""+this.negated);
		
		return res;
	}
	
	public Event(String docId, Map<String, String> mappedEvent, Map<String,String> mappedNegSpec, Node forest, String text){
		/*
		 * 
				String participantsNames = mappedEvent.get("participants");
		 */
		this.docId = docId;
		this.id = mappedEvent.get("id");
		this.eveType = mappedEvent.get("type");
		this.trigText = mappedEvent.get("trigger_text");
		Integer st = Integer.parseInt(mappedEvent.get("trigger_start"));
		Integer en = Integer.parseInt(mappedEvent.get("trigger_end"));
		this.trigIndex = new Pair<Integer>(st, en);

		//		System.out.println("Trigger: " + trigIndex + ", : " + text.substring(st,en));
		//		System.out.println(forest.getLeaves().size());

		this.trigger = forest.pair2Node(text, trigIndex);
		if (this.trigger==null){
			System.out.println(text);
			TextPipe.printData(mappedEvent);
			throw new IllegalStateException("trigger is null!");
		}
		
		if (mappedNegSpec != null){
			setNegated(mappedNegSpec.get("negated").equals("1"));
			setSpeculated(mappedNegSpec.get("speculated").equals("1"));
		}		
	}
	
//	public void addTrigger(String sentence, String parse){
//		Node root = new Node(parse);
//		//Node trigger = root.pair2Node(sentence, pair)
//	}
}
