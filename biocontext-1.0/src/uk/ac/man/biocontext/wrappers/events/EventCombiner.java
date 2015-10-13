package uk.ac.man.biocontext.wrappers.events;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;
import martin.common.ComparableTuple;
import martin.common.Pair;

import uk.ac.man.biocontext.dataholders.BindingEvent;
import uk.ac.man.biocontext.evaluate.EvaluateResult;
import uk.ac.man.biocontext.evaluate.Evaluate.Approx;
import uk.ac.man.biocontext.evaluate.Evaluate.Type;
import uk.ac.man.biocontext.evaluate.EvaluateResult.EvalType;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneNERWrapper;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipe.VerbosityLevel;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class EventCombiner extends Annotator {
	private Annotator geneAnnotator;
	private Annotator tokyoAnnotator;
	private Annotator turkuAnnotator;
	private Set<String> turkuCrashIDs;
	static final Set<String> filteringWhiteList = new HashSet<String>(Arrays.asList(new String[] {"/", "-", "is", "by", "as", "on", "up", "at", "be", "do", "if"}));
	static final Set<String> filteringBlackList = new HashSet<String>(Arrays.asList(new String[] {"the", "and", "in", "of", "cells", "to", "when", "patients", "are", "mice", "from", "both", "that", "mouse", "what"}));

	public static final String[] outputFields = new String[]{"id", "type", "trigger_start", "trigger_end", "trigger_text", "participants", "tokyo", "turku", "shallow_match", "inferred_23", "level"};

	@Override
	public void init(Map<String, String> data) {
		super.init(data);

		System.out.print("Loading annotators...");
		//		this.geneAnnotator = TextPipeClient.get(data, "genes", "db001", 57004, "farzin", "data_genes");
		//		this.tokyoAnnotator = TextPipeClient.get(data, "tokyo", "db001", 57022, "farzin", "data_e_tokyo");
		//		this.turkuAnnotator = TextPipeClient.get(data, "turku", "db001", 57021, "farzin", "data_e_turku");
		this.geneAnnotator = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser(), false);
		this.tokyoAnnotator = new PrecomputedAnnotator(EventMineWrapper.outputFields, "farzin", "data_e_tokyo", ArgParser.getParser(), false);
		this.turkuAnnotator = new PrecomputedAnnotator(TurkuWrapper.outputFields, "farzin", "data_e_turku", ArgParser.getParser(), false);
		System.out.print(" Done.\nLoading Turku crash data...");
		this.turkuCrashIDs = martin.common.Misc.loadStringSetFromFile(new File(data.get("turkuCrashes")));
		System.out.println(" Done. Loaded " + turkuCrashIDs.size() + " IDs.");
	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		String docid = data.get("doc_id");

		List<Map<String,String>> genes = geneAnnotator.process(data);

		if (genes.size() == 0)
			return new ArrayList<Map<String,String>>();

		List<Map<String,String>> turku = turkuAnnotator.process(data);
		List<Map<String,String>> tokyo = tokyoAnnotator.process(data);

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		Set<Map<String,String>> turkuSet = new HashSet<Map<String,String>>();
		turkuSet.addAll(turku);

		Map<String,String> renamedTurkuEvents = new HashMap<String,String>();

		//		if (turku.size() > 0 && turkuCrashIDs.contains(docid))
		//			throw new IllegalStateException("Found Turku events for doc " + docid + ", although it should have crashed!");

		for (Map<String,String> m : tokyo){
			Map<String,String> match = null;

			m.put("tokyo", "1");

			for (Map<String,String> n : turkuSet)
				if (matches(m, n, genes, genes, tokyo,  turku, Approx.APPROX, Type.EVENTS_UNION_DEEP)){
					match = n;
					break;
				}

			if (match != null){
				turkuSet.remove(match);
				renamedTurkuEvents.put(match.get("id"), m.get("id"));
				m.put("turku", "1");
			} else {
				if (!turkuCrashIDs.contains(docid))
					m.put("turku", "0");
			}

			res.add(m);
		}

		int c = tokyo.size() + 1;

		for (Map<String,String> m : turkuSet){
			String r = "E" + c++;
			if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
				System.out.println(data.get("doc_id") + "\t" + m.get("id") + "\t" + r);
			renamedTurkuEvents.put(m.get("id"), r);
			m.put("id", r);
		}

		for (Map<String,String> m : turkuSet){
			m.put("turku", "1");
			m.put("tokyo", "0");
			String[] fs = m.get("participants").split("\\|");
			for (int i = 0; i < fs.length; i++){
				String[] fss = fs[i].split(",");
				for (int j = 0; j < fss.length; j++){
					if (fss[j].startsWith("E") && renamedTurkuEvents.containsKey(fss[j]))
						fss[j] = renamedTurkuEvents.get(fss[j]);
				}
				fs[i] = martin.common.Misc.implode(fss, ",");
				if (fs[i].startsWith(","))
					fs[i] = fs[i].substring(1);
			}

			if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
				if (!m.get("participants").equals(martin.common.Misc.implode(fs, "|")))
					System.out.println(data.get("doc_id") + "\t" + m.get("id") + "\t" + m.get("participants") + "\t" + martin.common.Misc.implode(fs, "|"));

			m.put("participants", martin.common.Misc.implode(fs, "|"));

			res.add(m);
		}

		for (int i = 0; i < res.size(); i++)
			for (int j = i+1; j < res.size(); j++)
				if (
						res.get(i).containsKey("turku")
						&& res.get(j).containsKey("turku")
						&& matches(res.get(i), res.get(j), genes, genes, res, res, Approx.APPROX, Type.OPTIONS_SHALLOW)
						&& !res.get(i).get("turku").equals(res.get(j).get("turku")) 
						&& !res.get(i).get("tokyo").equals(res.get(j).get("tokyo"))
				){
					res.get(i).put("shallow_match", res.get(j).get("id"));
					res.get(j).put("shallow_match", res.get(i).get("id"));
					break;
				}

		for (Map<String,String> e : res){
			e.put("level", ""+getLevel(e, res, 0));

			if (BindingEvent.EVENT_TYPES.contains(e.get("type"))){
				sortBindingEventParticipants(e, genes);
			}
		}

		triggerFiltering(res, data.get("doc_text"));

		duplicateEvents(res, genes);

		return res;
	}

	private void triggerFiltering(List<Map<String, String>> res, String text) {
		LinkedList<String> removedIds = new LinkedList<String>();

		for (int i = 0; i < res.size(); i++){
			Map<String, String> item = res.get(i);
			if (shouldBeRemoved(item, text)){
				removedIds.add(item.get("id"));
				res.remove(i--);
			}
		}

		while (removedIds.size() > 0){
			String head = removedIds.pop();
			for (int i = 0; i < res.size(); i++){
				Set<String> parts = new HashSet<String>(Arrays.asList(res.get(i).get("participants").split("\\|"))); //only matters for regulation
				if (parts.contains(head)){
					removedIds.add(res.get(i).get("id"));
					res.remove(i--);
				}
			}
		}
	}

	private boolean shouldBeRemoved(Map<String, String> item, String text) {
		if (item.get("trigger_text").length() < 3 && !filteringWhiteList.contains(item.get("trigger_text"))){
			return true;
		}
		if (filteringBlackList.contains(item.get("trigger_text"))){
			return true;
		}
		if (item.get("trigger_text").matches("\\d+")){
			return true;
		}

		if (item.get("trigger_text").matches("[A-Z].*")){
			int start = Integer.parseInt(item.get("trigger_start"));
			List<Pair<Integer>> sentenceBreaks = martin.common.SentenceSplitter.toList(text);
			for (Pair<Integer> p : sentenceBreaks){
				if (p.getX() == start || start == 1){
					return false;
				}
			}
			return true;
		}
		
		if (Integer.parseInt(item.get("level")) > 2){
			return true;
		}

		return false;
	}

	private int getLevel(Map<String, String> e, List<Map<String, String>> events, int levelCounter) {
		if (levelCounter == 50){
			System.err.println("Probable infinite loop, level=50 for event " + e.get("id") + ", doc " + e.get("doc_id"));
			return 50;
		}

		int maxLevel = -1;

		//		cause
		for(String participant : e.get("participants").split("\\|")[0].split(",")){
			if (participant.startsWith("E")){
				int level = getLevel(Misc.getByID(events, participant), events, levelCounter + 1);
				if (level > maxLevel)
					maxLevel = level;
			}
		}

		//		theme
		for(String participant : e.get("participants").split("\\|")[1].split(",")){
			if (participant.startsWith("E")){
				int level = getLevel(Misc.getByID(events, participant), events, levelCounter + 1);
				if (level > maxLevel)
					maxLevel = level;
			}
		}

		return maxLevel + 1;			
	}

	public static void duplicateEvents(List<Map<String, String>> events, List<Map<String, String>> genes) {
		int size = events.size();
		duplicateEventsHelper(events, genes);

		while (size != events.size()){
			size = events.size();
			duplicateEventsHelper(events, genes);			
		}
	}

	public static void duplicateEventsHelper(List<Map<String, String>> events, List<Map<String, String>> genes) {
		Map<String, Set<String>> group2genes = new HashMap<String, Set<String>>();
		Map<String, String> gene2group = new HashMap<String, String>();
		List<Map<String, String>> inferredEvents = new ArrayList<Map<String, String>>();


		for(Map<String, String> entity : genes){
			String group = entity.get("entity_group");
			if(group != null){
				if(!group2genes.containsKey(group)){
					group2genes.put(group, new HashSet<String>());
				}
				group2genes.get(group).add(entity.get("id"));
				gene2group.put(entity.get("id"),group);
			}
		}

		Map<String, Set<String>> participant2events = new HashMap<String, Set<String>>();
		Map<String, List<String>> event2participants = new HashMap<String, List<String>>();

		for (Map<String, String> event : events){
			String eventID = event.get("id");
			event2participants.put(eventID, new ArrayList<String>());
			for (String participants : event.get("participants").split("\\|")){
				for(String participant : participants.split(",")){
					if (participant.startsWith("T"))
						participant = participant.substring(1);		//get rid of 'T'

					if(!participant2events.containsKey(participant)){
						participant2events.put(participant, new HashSet<String>());
					}
					participant2events.get(participant).add(eventID);

					event2participants.get(eventID).add(participant);
				}
			}
		}

		for (Map<String,String> event : events){

			//ignore binding atm
			String eventID = event.get("id");

			//test for cause group != theme group in regulation
			List<String> participants = event2participants.get(eventID);
			if (participants == null){
				throw new IllegalStateException("participants is null");
			}

			if (!
					(
							participants.size() == 2 && 
							gene2group.containsKey(participants.get(0)) &&
							gene2group.containsKey(participants.get(1)) &&
							gene2group.get(participants.get(0)).equals(gene2group.get(participants.get(1)))
					)
			){

				if (event.get("level").equals("0")
						&& !BindingEvent.EVENT_TYPES.contains(event.get("type"))){
					for (String participantToEnumerate : event2participants.get(eventID)){
						if (gene2group.containsKey(participantToEnumerate)){

							String group = gene2group.get(participantToEnumerate);

							Set<String> groupedNeighbours = group2genes.get(group);

							for (String neighbour : groupedNeighbours){

								Set<Map<String, String>> possibleInferredEvents = duplicate(event, participantToEnumerate, neighbour);
								Set<Map<String, String>> inferred = new HashSet<Map<String, String>>();

								if (participant2events.containsKey(neighbour)){

									for (String possibleEventIDMatch : participant2events.get(neighbour)){
										Map<String,String> possibleEventMatch = Misc.getByID(events, possibleEventIDMatch); // an event with neighbour as a participant
										boolean match = false;
										for (Map<String,String> possibleInferredEvent : possibleInferredEvents){
											if(matches(possibleInferredEvent, possibleEventMatch, genes, genes, events, events, Approx.APPROX, Type.EVENTS_TOKYO_DEEP)){
												match = true;
											}
											if(!match){
												inferred.add(possibleInferredEvent);
											}
										}

									}
								} else {
									inferred.addAll(possibleInferredEvents);
								}

								inferredEvents.addAll(inferred);
							}
						}
					}
				}
			}
		}		

		for (Map<String, String> inferredEvent : inferredEvents ){
			boolean matches = false;
			for (Map<String, String> event : events){
				if (matches(inferredEvent, event, genes, genes, events, events, Approx.APPROX, Type.EVENTS_TOKYO_DEEP)){
					matches = true;
					break;
				}
			}
			if (!matches){
				inferredEvent.put("id", "E"+(events.size() + 1 ));
				events.add(inferredEvent);
			}
		}
	}

	private static Set<Map<String, String>> duplicate(Map<String, String> event, String originalParticipant,
			String participantsToDuplicateFor) {

		Set<Map<String, String>> res = new HashSet<Map<String, String>>();


		Map<String, String> inferred = new HashMap<String, String>();
		for (String k : event.keySet()){
			inferred.put(k, event.get(k));
		}

		if (!originalParticipant.startsWith("E"))
			originalParticipant = "T" + originalParticipant;
		if (!participantsToDuplicateFor.startsWith("E"))
			participantsToDuplicateFor = "T" + participantsToDuplicateFor;

		String newParticipantString = "";

		int i = 0;
		for (String participants : event.get("participants").split("\\|")){
			int j = 0;

			for(String participant : participants.split(",")){
				if (j++ > 0)
					newParticipantString += ",";
				if (participant.equals(originalParticipant))
					newParticipantString += participantsToDuplicateFor;
				else
					newParticipantString += participant;
			}

			if (i++ == 0)
				newParticipantString += "|";
		}
		inferred.put("participants",newParticipantString);
		inferred.put("inferred_23", event.get("id"));

		res.add(inferred);
		return res;
	}

//	private boolean matches(Map<String, String> e1, Map<String, String> e2, List<Map<String, String>> genes, List<Map<String,String>> events) {
//		return matches(e1, e2, genes, genes, events, events, Approx.APPROX, Type.EVENTS_TOKYO_DEEP);
//	}
//
	public static Comparator<Map<String,String>> getEventComparator(){
		return new Comparator<Map<String,String>>(){
			@Override
			public int compare(Map<String, String> o1, Map<String, String> o2) {
				if (o1 == null && o2 == null)
					return 0;
				if (o1 == null)
					return -1;
				if (o2 == null)
					return 1;

				Integer a = Integer.parseInt(o1.get("id").substring(1));
				Integer b = Integer.parseInt(o2.get("id").substring(1));
				return a.compareTo(b);
			}			
		};
	}

	public static boolean matches(Map<String, String> event1,
			Map<String, String> event2, List<Map<String, String>> event1Genes,
			List<Map<String, String>> event2Genes, List<Map<String,String>> event1Events, List<Map<String,String>> event2Events, Approx a, Type t){

//		int event1start = Integer.parseInt(event1.get("trigger_start"));
//		int event1end = Integer.parseInt(event1.get("trigger_end"));
//		int event2start = Integer.parseInt(event2.get("trigger_start"));
//		int event2end = Integer.parseInt(event2.get("trigger_end"));
		String event1type = event1.get("type");
		String event2type = event2.get("type");
		if (
//								uk.ac.man.farzin.evaluate.Evaluate.overlap(event1start, event1end, event2start, event2end, a) && 
				event1type.equals(event2type)){
			if (t.toString().contains("SHALLOW")|| evalParticipantsDeep(event1, event2, event1Genes, event2Genes, event1Events, event2Events, a, t)){
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private static void sortBindingEventParticipants(Map<String, String> event, List<Map<String, String>> genes) {
		String[] parts = event.get("participants").substring(1).split(",");
		ComparableTuple<Integer,String>[] tuples = new ComparableTuple[parts.length];

		int c = 0;

		for (String p : parts){
//			System.out.println(p);
			Integer id = Integer.parseInt(Misc.getByID(genes, p.substring(1)).get("entity_id"));
			tuples[c++] = new ComparableTuple<Integer, String>(id,p);
		}

		Arrays.sort(tuples);
		String[] ids = new String[tuples.length];
		for (int i = 0; i < tuples.length; i++)
			ids[i] = tuples[i].getB();
		event.put("participants","|" + martin.common.Misc.implode(ids, ","));		
	}

	public static List<EvaluateResult> evaluate(List<Map<String, String>> predEvents,
			List<Map<String, String>> goldEvents, List<Map<String, String>> predGenes,
			List<Map<String, String>> goldGenes, Approx a, Type t) {
		List<EvaluateResult> res = new ArrayList<EvaluateResult>();

		for (Map<String,String> p : predEvents){
			if (!p.containsKey("invalid")){
				EvalType type = EvalType.FP;

				for (Map<String,String> g : goldEvents){
					if (matches(p,g,predGenes,goldGenes,predEvents,goldEvents, a, t)){
						type = EvalType.TP;
						break;
					}
				}

				EvaluateResult e = new EvaluateResult(type, p, p.get("trigger_start"), p.get("trigger_end"));
				e.getInfo().put("id", p.get("id"));
				e.getInfo().put("type", p.get("type"));
				e.getInfo().put("participants", p.get("participants"));
				e.getInfo().put("info", p.get("participants") + "; " + p.get("trigger_start") + "," + p.get("trigger_end"));
				res.add(e);
			}
		}

		for (Map<String,String> g : goldEvents){
			EvalType type = EvalType.FN;

			for (Map<String,String> p : predEvents){
				if (matches(p,g,predGenes,goldGenes, predEvents, goldEvents, a, t) && !p.containsKey("invalid")){
					type = EvalType.TP;
					break;
				}
			}

			if (type == EvalType.FN){
				EvaluateResult e = new EvaluateResult(type, g, g.get("trigger_start"), g.get("trigger_end"));
				e.getInfo().put("id", g.get("id"));
				e.getInfo().put("type", g.get("type"));
				e.getInfo().put("participants", g.get("participants"));
				e.getInfo().put("info", g.get("participants") + "; " + g.get("trigger_start") + "," + g.get("trigger_end"));
				res.add(e);
			}
		}

		return res;
	}

	private static boolean evalParticipantsDeep(Map<String, String> p,
			Map<String, String> g, List<Map<String, String>> predGenes,
			List<Map<String, String>> goldGenes, List<Map<String,String>> predEvents, List<Map<String,String>> goldEvents, Approx a, Type t) {

		if (p.get("participants") == null)
			throw new IllegalStateException();
		if (g.get("participants") == null)
			throw new IllegalStateException();

		if (p.get("type").equals("Binding") && g.get("type").equals("Binding")){
			List<Map<String,String>> pg = new ArrayList<Map<String,String>>();
			List<Map<String,String>> gg = new ArrayList<Map<String,String>>();

			for (String pid : p.get("participants").split("\\|")[1].split(",")){
				if (pid.startsWith("T")){
					pg.add(Misc.getByID(predGenes, pid.substring(1)));
				}
			}

			for (String gid : g.get("participants").split("\\|")[1].split(",")){
				if (gid.startsWith("T")){
					gg.add(Misc.getByID(goldGenes, gid.substring(1)));
				}
			}

			List<EvaluateResult> r = GeneNERWrapper.evaluate(pg, gg, a);

			for (EvaluateResult e : r){
				if (e.getType() != EvaluateResult.EvalType.TP)
					return false;
			}

			return true;
		} else {
			String[] predParticipants = p.get("participants").split("\\|");
			String[] goldParticipants = g.get("participants").split("\\|");

			for (int i = 0; i < 2; i++){
				String pParticipant = predParticipants[i];
				String gParticipant = goldParticipants[i];
				
				if (pParticipant.length() == 0 && gParticipant.length() > 0)
					return false;
				if (pParticipant.length() > 0 && gParticipant.length() == 0)
					return false;

				if (pParticipant.length() > 0){
					if (!pParticipant.startsWith(gParticipant.substring(0,1)))
						return false;
					if (pParticipant.startsWith("E")){
						Map<String,String> pe = Misc.getByID(predEvents, pParticipant);
						Map<String,String> ge = Misc.getByID(goldEvents, gParticipant);
						if (ge == null)
							throw new IllegalStateException("could not find " + gParticipant);
						boolean res = matches(pe, ge, predGenes, goldGenes, predEvents, goldEvents, a, t);
						if (!res)
							return false;
					} else if (pParticipant.startsWith("T")){
						Map<String,String> pg = Misc.getByID(predGenes, pParticipant.substring(1));
						Map<String,String> gg = Misc.getByID(goldGenes, gParticipant.substring(1));
						if (pg == null)
							throw new IllegalStateException("Could not find " + pParticipant);
						boolean res = GeneNERWrapper.equalsIgnoreID(pg, gg, a);
						if (!res)
							return false;
					}
				}
			}

			return true;
		}
	}

	public static boolean hasParticipant(Map<String, String> event, String ID) {
		if (!event.get("participants").contains(ID)){
			return false;
		} else {
			for (String p : event.get("participants").split("\\|")){
				for (String q : p.split(",")){
					if (q.equals(ID)){
						return true;
					}
				}
			}
			return false;
		}
	}

}