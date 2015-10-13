package uk.ac.man.biocontext.wrappers.genener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import martin.common.Misc;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.entitytagger.matching.matchers.UnionMatcher;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipeClient;

public class GeneCombiner extends Annotator{
	Annotator gnatClient=null, genetukitClient=null;
	Map<String,String> toSpecies=null;
	public static final String[] outputFields = new String[]{"id","entity_id","entity_start","entity_end","entity_term","entity_group","entity_species","confidence","gnat","genetukit"};

	@Override
	public void init(Map<String, String> data) {
		genetukitClient = TextPipeClient.get(data, "genetukit", "db001", 57003, "farzin", "data_g_genetukit");
		gnatClient = TextPipeClient.get(data, "gnat", "db001", 57002, "farzin", "data_g_gnat");
		
		System.out.println("Loading species map...");
		toSpecies = Misc.loadMap(new File(data.get("toSpeciesMap")));		
		System.out.println("Done.");
	}

	@Override
	public boolean isConcurrencySafe() {
		return true;
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}
	
		

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String docid = data.containsKey("doc_id") ? data.get("doc_id") : "none";
		
		List<Map<String,String>> gnatData = getData(gnatClient, data);
		List<Map<String,String>> genetukitData = getData(genetukitClient, data);

		List<Matcher> matchers = new ArrayList<Matcher>();
		matchers.add(new TextPipeMatcher(genetukitData));
		matchers.add(new TextPipeMatcher(gnatData));
		Matcher matcher = new UnionMatcher(matchers, false);

		List<Mention> mentions = matcher.match(data.get("doc_text"),docid);

		removeFaultyMentions(mentions);
		
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();
		if (mentions == null){
			System.err.println("mentions == null for doc_id=" + docid);
			return res;
		}

		Collections.sort(mentions);
		Matcher.detectEnumerations(mentions, data.get("doc_text"));

		int c = 0;
		for (Mention m : mentions){
			Map<String,String> map = new HashMap<String, String>();

			if (m.getIdsToString().contains("?"))
				m.setIds(new String[]{m.getMostProbableID()});

			if (Misc.implode(m.getIds(),"|").contains("|")){ //check if either m.getIds().length > 1 or m.getIds()[0].contains("|")
				map.put("entity_id", "0");
				map.put("entity_species", "0");
			} else {
				map.put("entity_id", m.getIds()[0]);
				map.put("entity_species", getSpecies(m.getIds()));
			}
			
			map.put("entity_start", ""+m.getStart());
			map.put("entity_end", ""+m.getEnd());
			map.put("entity_term", ""+m.getText());
			

			map.put("gnat", contains(gnatData, m.getStart()));
			map.put("genetukit", contains(genetukitData, m.getStart()));
			
			String group = null;
			if (m.getComment().indexOf("group: ") != -1){
				int s = m.getComment().indexOf("group: ") + 7;
				group = m.getComment().substring(s,m.getComment().indexOf(",",s));
			}

			map.put("entity_group", group);
			map.put("id",""+c++);

			if (m.getProbabilities() != null && m.getProbabilities().length == 1)
				map.put("confidence", ""+m.getProbabilities()[0]);

			res.add(map);
		}

		return res;
	}

	private List<Map<String, String>> getData(Annotator client, Map<String, String> data) {
		String doc_id = data.get("doc_id");
		
		if (!doc_id.startsWith("PMC") || !doc_id.contains(".")){
			return client.process(data);
		}
		
		String[] fs = doc_id.split("\\.");
		
		Document d = new Document(fs[0],null,null,null,null,null,null,null,null,null,null,null,null,null,null);
		
		int docStart = Integer.parseInt(fs[2]);
		int docEnd = docStart + data.get("doc_text").length();
		
		List<Map<String,String>> intermediate = client.processDoc(d);
		List<Map<String,String>> res = new ArrayList<Map<String,String>>();
		
		for (Map<String,String> m : intermediate){
			int s = Integer.parseInt(m.get("entity_start"));
			int e = Integer.parseInt(m.get("entity_end"));
			
			if (s >= docStart && s < docEnd && e >= docStart && e <= docEnd){
				if (m.containsKey("doc_id"))
					m.put("doc_id", doc_id);

				m.put("entity_start", ""+(s-docStart));
				m.put("entity_end", ""+(e-docStart));

				res.add(m);
			}
		}
		
		return res;
	}

	private void removeFaultyMentions(List<Mention> mentions) {
		for (int i = 0; i < mentions.size(); i++)
			if (mentions.get(i).getText() == null || mentions.get(i).getText().equals("null"))
				mentions.remove(i--);
	}

	private String contains(List<Map<String, String>> gnatData, int start) {
		for (Map<String,String> m : gnatData)
			if (Integer.parseInt(m.get("entity_start")) == start)
				return "1";
		
		return "0";
	}

	private String getSpecies(String[] ids) {
		String[] res = new String[ids.length];
		for (int i = 0; i < ids.length; i++){
			res[i] = toSpecies.containsKey(ids[i]) ? toSpecies.get(ids[i]) : "0";
		}
		return Misc.implode(res, "|");
	}
}