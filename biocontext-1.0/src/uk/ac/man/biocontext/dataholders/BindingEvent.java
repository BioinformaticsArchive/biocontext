package uk.ac.man.biocontext.dataholders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.Pair;

public class BindingEvent extends Event {
	//private List<String> themeIds;
	private List<Node> themes = new ArrayList<Node>();
	public static final Set<String> EVENT_TYPES = new HashSet<String>(Arrays.asList(new String[] {"Binding"}));

	
//	public BindingEvent(String docId, String id, String eveType, Node trigger,
//			String trigText, Pair<Integer> trigIndex, List<String> themeIds, List<Node> themes) {
//		super(docId, id, eveType, trigText, trigIndex, trigger);
//		//setThemeIds(themeIds);
//		setThemes(themes);
//	}
	
	public BindingEvent(String docId, Map<String, String> mappedEvent,
			Map<String, String> mappedNegSpec,
			List<Map<String, String>> mappedThemes, Node forest, String text) {
		super(docId, mappedEvent, mappedNegSpec, forest, text);
		
		for (Map<String, String> mappedTheme : mappedThemes){
			Pair<Integer> themeIndex = new Pair<Integer>(Integer.parseInt(mappedTheme.get("entity_start")), Integer.parseInt(mappedTheme.get("entity_end")));
			themes.add(forest.pair2Node(text, themeIndex));
		}
			
	}
	public void setThemes(List<Node> themes) {
		this.themes = themes;
	}
	public List<Node> getThemes() {
		return themes;
	}
//	public void setThemeIds(List<String> themeIds) {
//		this.themeIds = themeIds;
//	}
//	public List<String> getThemeIds() {
//		return themeIds;
//	}
	@Override
	public Node getTheme() {
		throw new IllegalStateException("getTheme can't be used on Binding events. Use getThemes() instead.");
	}
	@Override
	public Node getNode() {
		return this.getTrigger();
	}
	
}
