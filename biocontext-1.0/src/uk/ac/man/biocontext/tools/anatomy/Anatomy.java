package uk.ac.man.biocontext.tools.anatomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;
import martin.common.Pair;
import martin.common.SentenceSplitter;
import martin.common.Tuple;

import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.GDepWrapper;
import uk.ac.man.biocontext.wrappers.LinnaeusWrapper;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class Anatomy extends Annotator {
	private Annotator anatomyAnnotator;
	private Annotator eventAnnotator;
	private Annotator gdepAnnotator;
	public static final String[] outputFields = new String[]{"id", "type", "trigger_start", "trigger_end", "trigger_text", "participants", "tokyo", "turku", "shallow_match", "inferred_23", "inferred_31", "level", "anatomy_id"};

	@Override
	public void init(Map<String, String> data) {
//		this.anatomyAnnotator = PubToolsClient.get(data, "anatomy", "db001", 57001, "farzin", "data_l_anatomy");
//		this.eventAnnotator = PubToolsClient.get(data, "events", "db001", 57023, "farzin", "data_events");
//		this.gdepAnnotator = PubToolsClient.get(data, "gdep", "db001", 57011, "farzin", "data_p_gdep");
		this.anatomyAnnotator = new PrecomputedAnnotator(LinnaeusWrapper.outputFields, "farzin", "data_l_anatomy", ArgParser.getParser());
		this.eventAnnotator = new PrecomputedAnnotator(EventCombiner.outputFields, "farzin", "data_events", ArgParser.getParser());
		this.gdepAnnotator = new PrecomputedAnnotator(GDepWrapper.outputFields, "farzin", "data_p_gdep", ArgParser.getParser());
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String text = data.get("doc_text");

		List<Map<String,String>> events = eventAnnotator.process(data);
		if (events.size() == 0)
			return new ArrayList<Map<String,String>>();

		List<Map<String,String>> anatomy = anatomyAnnotator.process(data);
		if (anatomy.size() == 0)
			return events;

		List<Map<String,String>> gdepData = gdepAnnotator.process(data);

		Collections.sort(events, EventCombiner.getEventComparator());
		Misc.sort(anatomy);
		Misc.sort(gdepData);

		SentenceSplitter ssp = new SentenceSplitter(text);

		int i = 0;

		for (Pair<Integer> p : ssp){
			String sentence = text.substring(p.getX(), p.getY());

			List<Map<String,String>> events_ = filter(events, p, "trigger_start", "trigger_end", "trigger_text", 1);
			List<Map<String,String>> anatomy_ = filter(anatomy, p, "entity_start", "entity_end", "entity_term", 3);
			int sStart = p.getX();

			Tuple<List<Integer>, List<String>> gdepTree = null;
			List<Integer> gdepMap = null;
			try {
				String gdepSentenceData = Misc.getByID(gdepData, i).get("gdep_data");
				gdepTree = parseGDep(gdepSentenceData);
				gdepMap = mapGdep(gdepTree, sentence);
			} catch (Exception e){
				System.err.println("error\tGdep parser error\t" + data.get("doc_id") + "\t" + e.toString());
			}
			
			if (events_.size() > 0 && anatomy_.size() > 0){
				for (Map<String,String> e : events_){
					int ts = Integer.parseInt(e.get("trigger_start"));
					int te = Integer.parseInt(e.get("trigger_end"));
					
					Map<String,String> m = e;
					
					if (anatomy_.size() == 1){
						m.put("anatomy_id", anatomy_.get(0).get("id"));
					} else {

						String id = null;
						if (sentence.indexOf(" in ", te) != -1){
							int in = sentence.indexOf(" in ", te);
							int mindist = 0;
							for (Map<String,String> a : anatomy_){
								int dist = Integer.parseInt(a.get("entity_start")) - in;
								if (dist > 0 && (id == null || mindist > dist)){
									mindist=dist;
									id=a.get("id");
								}
							}
						} 
						if (id == null){ 
							if (gdepTree != null && gdepMap != null){
								Map<String,Mention> anatomyMentions = toMentions(anatomy_);
								Tuple<Integer,Integer> triggerTokens = getTokens(ts - sStart, te - sStart, gdepMap);
								id = getClosest(triggerTokens, anatomyMentions, gdepTree.getA(), gdepMap);
							} else {
								int mindist = 0;

								for (Map<String,String> a : anatomy_){
									int dist = Math.abs(Integer.parseInt(a.get("entity_start")) - ts);
									if (id == null || mindist > dist){
										mindist=dist;
										id=a.get("id");
									}
								}
							}
						}

						if (id != null)
							m.put("anatomy_id", id);
					}

					if (m.get("anatomy_id") != null && Misc.getByID(anatomy_, m.get("anatomy_id")).get("entity_group") != null){
						markInference(m, Misc.getByID(anatomy_, m.get("anatomy_id")), anatomy_);
					}
				}
			}

			i++;
		}

		doInference(events);
		
		return events;		
	}

	private void doInference(List<Map<String, String>> events) {
		List<Map<String,String>> toAdd = new ArrayList<Map<String,String>>();
		
		int nextID = 1;
		for (Map<String,String> e : events){
			int id = Integer.parseInt(e.get("id").substring(1));
			if (id >= nextID)
				nextID = id+1;
		}
		
		for (Map<String,String> m : events){
			if (m.containsKey("tmp_inference")){
				String[] ids = m.get("tmp_inference").split(",");
				for (String id : ids){
					if (id.length() > 0){
						Map<String,String> inferredEvent = new HashMap<String,String>();
						for (String k : m.keySet()){
							inferredEvent.put(k, m.get(k));
						}
						inferredEvent.put("id", "E" + nextID++);
						inferredEvent.put("anatomy_id", id);
						inferredEvent.put("inferred_31", m.get("id"));
						toAdd.add(inferredEvent);
					}
				}
			}
		}
		
		events.addAll(toAdd);		
	}

	private void markInference(Map<String, String> event, Map<String, String> anatomy, List<Map<String, String>> anatomyList) {
		Set<String> anatomyIDs = new HashSet<String>();

		for (Map<String,String> m : anatomyList){
			if (m != anatomy && m.get("entity_group") != null && m.get("entity_group").equals(anatomy.get("entity_group"))){
				anatomyIDs.add(m.get("id"));
			}
		}

		event.put("tmp_inference", martin.common.Misc.implode(anatomyIDs.toArray(), ","));
	}

	private Map<String,Mention> toMentions(List<Map<String,String>> data){

		Map<String,Mention> res = new HashMap<String,Mention>();

		for (Map<String,String> d : data){
			Mention m = new Mention(d.get("entity_id"), Integer.parseInt(d.get("entity_start")), Integer.parseInt(d.get("entity_end")), d.get("entity_term"));

			if (d.containsKey("id"))
				res.put(d.get("id"), m);
			else
				throw new IllegalStateException("toMentions requires that the incoming data have associated id values");
		}

		return res;
	}

	private static String getClosest(Tuple<Integer, Integer> triggerTokens,
			Map<String,Mention> mentions, List<Integer> tree, List<Integer> nlpMap) {

		if (triggerTokens == null)
			return null;
		
		String best = null;
		int bestDist = -1;

		for (String k : mentions.keySet()){
			Mention g = mentions.get(k);
			Tuple<Integer,Integer> geneTokens = getTokens(g.getStart(), g.getEnd(), nlpMap);
			if (geneTokens != null){
				int d = getDistance(geneTokens, triggerTokens, tree);
				if (best == null || d < bestDist){
					best = k;
					bestDist = d;
				}
			}
		}

		return best;
	}

	private static int getDistance(Tuple<Integer, Integer> genetokens,
			Tuple<Integer, Integer> triggertokens, List<Integer> tree) {

		int res = -1;

		for (int i = genetokens.getA(); i < genetokens.getB()+1; i++)
			for (int j = triggertokens.getA(); j < triggertokens.getB()+1; j++){
				int d = getDistance(i,j,tree);
				if (res == -1 || res > d)
					res = d;
			}
		return res;
	}

	private static int getDistance(int i, int j, List<Integer> tree) {
		Map<Integer,Integer> dists = new HashMap<Integer,Integer>();

		int start = i;
		int prev = 0;
		while (start != -1){
			dists.put(tree.get(start), prev+1);
			prev++;
			start = tree.get(start);	
		}

		start = j;
		prev = 0;
		while (true){

			if (dists.containsKey(start))
				return dists.get(start) + prev;

			assert(start != -1);

			start = tree.get(start);
			prev++;
		}
	}

	private static List<Integer> mapGdep(Tuple<List<Integer>, List<String>> gdepTree, String text) {
		StringBuffer t2 = new StringBuffer(text);

		for (int i = 0; i < t2.length(); i++)
			if (t2.codePointAt(i) > 127)
				t2.setCharAt(i, '?');

		text = t2.toString().toLowerCase();

		int progress = 0;

		List<Integer> sentenceRes = new ArrayList<Integer>();
		List<String> terms = gdepTree.getB();

		for (String term : terms){
			StringBuffer term2 = new StringBuffer(term);

			for (int i = 0; i < term.length(); i++)
				if (term.codePointAt(i) > 127)
					term2.setCharAt(i, '?');

			term = term2.toString().toLowerCase();

			int start = text.indexOf(term,progress);

			if (start != -1){
				progress = start + term.length();
				sentenceRes.add(start);
			}

			if (start == -1 && text.indexOf(term) != -1)
				throw new IllegalStateException("could not map term " + term);
		}

		return sentenceRes;
	}	

	private static Tuple<List<Integer>, List<String>> parseGDep(String data){
		String[] lines = data.split("\n");

		List<Integer> tree = new ArrayList<Integer>();
		List<String> terms = new ArrayList<String>();

		for (String s : lines){
			String[] fs = s.split("\t");
			int parent = Integer.parseInt(fs[6]) - 1;
			String term = fs[1];

			if (parent == -2)
				parent = -1; //very seldom, Gdep will throw an error and set parent = -1 (which becomes -2 for us)

			tree.add(parent);
			terms.add(term);
		}

		if (tree.size() > 0)
			return new Tuple<List<Integer>,List<String>>(tree, terms);
		else
			return null;
	}

	private List<Map<String, String>> filter(List<Map<String, String>> list,
			Pair<Integer> coords, String startLabel, String endLabel, String termLabel, int minLength) {

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		int sentStart = coords.getX();
		int sentEnd = coords.getY();

		for (Map<String,String> m : list){
			int s = Integer.parseInt(m.get(startLabel));
			int e = Integer.parseInt(m.get(endLabel));
			int length = m.get(termLabel).length();

			if (s >= sentStart && e <= sentEnd && length >= minLength)
				res.add(m);
		}

		return res;
	}

	private static Tuple<Integer, Integer> getTokens(int start, int end,
			List<Integer> lmap) {

		int start_token = -1;
		int end_token = -1;

		for (int i = 0; i < lmap.size(); i++){
			int ts = lmap.get(i);
			if (start_token == -1 && (start >= ts && (i == lmap.size()-1 || start < lmap.get(i+1))))
				start_token = i;
			if (ts < end)
				end_token = i;
		}

		if (end_token >= start_token && start_token >= 0 && end_token < lmap.size()) {
			return new Tuple<Integer,Integer>(start_token, end_token);
		} else {
			return null;
		}
	}
}
