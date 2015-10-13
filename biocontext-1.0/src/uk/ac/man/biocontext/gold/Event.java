package uk.ac.man.biocontext.gold;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import martin.common.Pair;



public class Event {
	private String docId;
	private String id;
	private String eveType;
	private Pair<Integer> triggerOffset;
	private String triggerText;
	private List<String> themes;
	private List<String> causes;
	private String negated;
	private String speculation;
	
	public Event(String docId, String id, String eveType,
			Pair<Integer> trigerOffset, String triggerText, List<String> themes,
			List<String> cause, String negated2, String speculation) {
		this.docId = docId;
		this.id = id;
		if (eveType.contains("hosphorylation")){
			this.eveType = "Phosphorylation";
		}else {
			this.eveType = eveType;
		}
		this.triggerOffset = trigerOffset;
		this.triggerText = triggerText;
		this.themes = themes;
		this.causes = cause;
		this.negated = negated2;
		this.speculation = speculation;
	}

	public String getDocId() {
		return docId;
	}

	public String getId() {
		return id;
	}

	public String getEveType() {
		return eveType;
	}

	public Pair<Integer> getTriggerOffset() {
		return triggerOffset;
	}

	public String getTriggerText() {
		return triggerText;
	}

	public List<String> getThemes() {
		return themes;
	}

	public List<String> getCauses() {
		return causes;
	}

	public String getNegated() {
		return negated;
	}

	public String getSpeculation() {
		return speculation;
	}
	
	public void setTriggerOffset(Pair<Integer> triggerOffset) {
		this.triggerOffset = triggerOffset;
	}
	
	public String toString(){
		return docId + "\t" + id + "\t" + eveType + "\t" + triggerOffset + "\t" + triggerText + "\t" + implode(causes, ",")  + "\t" + implode(themes, ",")
		+ "\t" + negated + "\t" + speculation; 
	}
	
	private String implode(Collection<String> collection, String separator){
		//TODO move to martin.common.Misc and fix
		if (collection == null)
			return null;
		
		if (collection.size() == 0)
			return "";

		StringBuffer sb = new StringBuffer();

		Iterator<String> iterator = collection.iterator();
		
		sb.append(iterator.next().toString());

		
		while (iterator.hasNext())
			sb.append(separator + iterator.next().toString());

		return sb.toString();
	}

	public String getParticipants() {
		String s = implode(causes, ",");
		s += "|";
		s += implode(themes, ",");
		return s;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public boolean isNegated(){
		return negated.equals("non-exist");
	}

	public boolean isSpeculated(){
		return !speculation.equals("certain");
	}
}
