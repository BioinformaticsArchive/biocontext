package uk.ac.man.biocontext.dataholders;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import martin.common.Pair;



public class RegulationEvent extends Event {
	public static final Set<String> EVENT_TYPES = new HashSet<String>(Arrays.asList(new String[] {"Regulation", "Positive_regulation", "Negative_regulation"}));
	
	private String themeType;
	private String themeId;
	private Participant theme;
	
	boolean hasCause = false;
	private String causeType = null;
	private String causeId = null;
	private Participant cause;
		
	public RegulationEvent(String docId, String id, String eveType, Node trigger,
			String trigText, Pair<Integer> trigIndex, String themeType, Participant theme, String themeId) {
		super(docId, id, eveType, trigText, trigIndex, trigger);
		if (theme == null)
			throw new IllegalStateException();
		this.themeType = themeType;
		this.theme = theme;
		this.themeId = themeId;
		this.hasCause = false;
	}
	
	public RegulationEvent(String docId, String id, String eveType, Node trigger,
			String trigText, Pair<Integer> trigIndex, String themeType, Participant theme, String themeId,
			String causeType, Participant cause, String causeId) {
		super(docId, id, eveType, trigText, trigIndex, trigger);
		if (theme == null)
			throw new IllegalStateException();
		this.themeType = themeType;
		this.theme = theme;
		this.themeId = themeId;
		this.hasCause = true;
		this.cause = cause;
		this.causeId = causeId;
	}
	
	public RegulationEvent(String docId, Map<String, String> mappedEvent,
			Map<String, String> mappedNegSpec,
			String themeType, Map<String, String> mappedTheme,
			String causeType, Map<String, String> mappedCause,
			Node forest, String text) {
		
		
		super(docId, mappedEvent, mappedNegSpec, forest, text);
		
//		System.out.println("mappedEvent is " + mappedEvent);
//		System.out.println("mappedTheme is " + mappedTheme);
//		System.out.println("mappedCause is " + mappedCause);
//		System.out.println("themeType is " + themeType);
//		System.out.println("causeType is " + causeType);

		
		this.themeType = themeType;
		if (themeType.equals("T")){
			Integer st = Integer.parseInt(mappedTheme.get("entity_start"));
			Integer en = Integer.parseInt(mappedTheme.get("entity_end"));
			Pair<Integer> themeIndex = new Pair<Integer>(st, en);
			this.theme = forest.pair2Node(text, themeIndex);
		}else if (themeType.equals("E")){
			//TODO here it needs to be the complete recursive procedure of creating new events from maps
			//as we are doing now; i.e. approx. lines 144-240 of NegmoleTraining
			//for now, we just look at the trigger.
			Integer st = Integer.parseInt(mappedTheme.get("trigger_start"));
			Integer en = Integer.parseInt(mappedTheme.get("trigger_end"));
			Pair<Integer> themeIndex = new Pair<Integer>(st, en);
			try{
				this.theme = forest.pair2Node(text, themeIndex);
			}catch(StringIndexOutOfBoundsException e){
				System.out.println(this.getId());
				System.out.println(this.getEveType());
			}
		}else throw new IllegalStateException("theme type encountered other than T or E");
		
		if (causeType.equals("")){
			this.hasCause = false;
			this.setCause(null);
		}else if (causeType.equals("T")){
			this.causeType = "T";
			try{
			Integer st = Integer.parseInt(mappedCause.get("entity_start"));
			Integer en = Integer.parseInt(mappedCause.get("entity_end"));
			Pair<Integer> causeIndex = new Pair<Integer>(st, en);
			this.cause = forest.pair2Node(text, causeIndex);
			}
			catch(NumberFormatException e){
				System.out.println(mappedCause);
				System.out.println(mappedCause.get("entity_start"));
				throw e;
			}
			
		}else if(causeType.equals("E")){
			this.causeType = "E";
			Integer st = Integer.parseInt(mappedCause.get("trigger_start"));
			Integer en = Integer.parseInt(mappedCause.get("trigger_end"));
			Pair<Integer> causeIndex = new Pair<Integer>(st, en);
			this.cause = forest.pair2Node(text, causeIndex);
		}else throw new IllegalStateException("cause type encountered other than T or E");
	}

	public boolean hasCause(){
		return this.hasCause;
	}
	
	public void setCause(Participant cause) {
		this.cause = cause;
	}
	public Object getCause() {
		return cause;
	}
	public void setCauseId(String causeId) {
		this.causeId = causeId;
	}
	public String getCauseId() {
		return causeId;
	}
	public void setCauseType(String causeType) {
		this.causeType = causeType;
	}
	public String getCauseType() {
		return causeType;
	}
//	public void setTheme(Participant theme) {
//		this.theme = theme;
//	}
	public Object getRealTheme(){
		return theme;
	}
	public Node getTheme() {
		if (theme == null){
			throw new IllegalStateException("theme is null here!");
		}
		//return (Node) this.theme;
		return this.theme.getNode();
		
	}
	public void setThemeId(String themeId) {
		this.themeId = themeId;
	}
	public String getThemeId() {
		return themeId;
	}
	public void setThemeType(String themeType) {
		this.themeType = themeType;
	}
	public String getThemeType() {
		return themeType;
	}

	@Override
	public Node getNode() {
		return this.getTrigger();
		
	}
	
	public Participant getActualTheme(){
			return this.theme;
	}
	
	public Participant getActualCause(){
		return this.cause;
}
}
