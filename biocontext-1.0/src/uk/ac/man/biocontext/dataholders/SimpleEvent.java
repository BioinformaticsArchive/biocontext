package uk.ac.man.biocontext.dataholders;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import martin.common.Pair;

public class SimpleEvent extends Event {
	private Node theme;
	private String themeId;
	public static final Set<String> EVENT_TYPES = new HashSet<String>(Arrays.asList(new String[] {"Transcription", "Gene_expression", "Phosphorylation", "Protein_catabolism", "Localization"}));
	
	
	public SimpleEvent(String docId, String id, String eveType, Node trigger, String trigText, 
			Pair<Integer> trigIndex, String themeId, Node theme) {
		super(docId, id, eveType, trigText, trigIndex, trigger);
		setTheme(theme);
		setThemeId(themeId);
	}
	public SimpleEvent(String docId, Map<String, String> mappedEvent, Map<String,String> mappedNegSpec, Map<String,String> mappedTheme, Node forest, String text){
		super(docId, mappedEvent, mappedNegSpec, forest, text);
			
		Pair<Integer> themeIndex = new Pair<Integer>(Integer.parseInt(mappedTheme.get("entity_start")), Integer.parseInt(mappedTheme.get("entity_end")));				

		theme = forest.pair2Node(text, themeIndex);	

	}
	public void setTheme(Node theme) {
		this.theme = theme; 
	}
	public Node getTheme() {
		return theme;
	}
	public void setThemeId(String themeId) {
		this.themeId = themeId;
	}
	public String getThemeId() {
		return themeId;
	}
	@Override
	public Node getNode() {
		return this.getTrigger();
	}
	


}
