package uk.ac.man.biocontext.tools.negmole;

import java.io.File;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import martin.common.ArgParser;
import martin.common.Loggers;
import uk.ac.man.biocontext.dataholders.BindingEvent;
import uk.ac.man.biocontext.dataholders.Event;
import uk.ac.man.biocontext.dataholders.Node;
import uk.ac.man.biocontext.dataholders.RegulationEvent;
import uk.ac.man.biocontext.dataholders.SimpleEvent;
import uk.ac.man.biocontext.dataholders.Exception.ParseFailedException;
import uk.ac.man.biocontext.tools.negmole.Exceptions.UnrecognisedEventTypeException;
import uk.ac.man.biocontext.tools.negmole.Features.Classes;
import uk.ac.man.biocontext.tools.negmole.Features.NegOrSpec;
import uk.ac.man.biocontext.tools.negmole.svmWrapper.SvmPerfWrapper;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipeClient;

//import jnisvmlight.FeatureVector;
//import jnisvmlight.LabeledFeatureVector;
//import jnisvmlight.SVMLightModel;
//import jnisvmlight.SVMLightInterface;
//import jnisvmlight.TrainingParameters;

/**
 * @author Farzaneh
 */
public class NegmoleBuild {

	public static boolean sem;

	private static List<Event> getGoldData(DocumentIterator documents) throws UnrecognisedEventTypeException{
		ArgParser ap = ArgParser.getParser();

		Annotator ptcMcc;
		if (ap.containsKey("sem"))
			ptcMcc = ap.containsKey("dbHost-farzin") ? TextPipeClient.get(null, null, "localhost", 59014, "farzin", "data_bionlp_mcc") : new TextPipeClient("localhost",59014);
		else
			ptcMcc = ap.containsKey("dbHost-farzin") ? TextPipeClient.get(null, null, "localhost", 59010, "farzin", "gold_bionlp_mcclosky") : new TextPipeClient("localhost",59010);

		Annotator ptcEv = ap.containsKey("dbHost-farzin") ? TextPipeClient.get(null, null, "localhost", 59020, "farzin", "gold_bionlp_events") : new TextPipeClient("localhost",59020);
		Annotator ptcEn = ap.containsKey("dbHost-farzin") ? TextPipeClient.get(null, null, "localhost", 59002, "farzin", "gold_bionlp_entities") : new TextPipeClient("localhost",59002);
		Annotator ptcNegSpec = ap.containsKey("dbHost-farzin") ? TextPipeClient.get(null, null, "localhost", 59030, "farzin", "gold_bionlp_negspec") : new TextPipeClient("localhost",59030);

		List<Event> allEvents = new ArrayList<Event>();

		//processing per document

		for (Document d : documents){
			try {
				allEvents.addAll(processDocument(d, ptcEn, ptcMcc, ptcEv, ptcNegSpec));
			} catch (ParseFailedException e) {
				continue;
			}
		}//end of a single document processing

		return allEvents;
	}

	/**
	 * gets a document and returns all its Events.
	 * 
	 * @param d
	 * @param ptcEn
	 * @param ptcMcc
	 * @param ptcEv
	 * @param ptcNegSpec
	 * @return
	 * @throws UnrecognisedEventTypeException
	 * @throws ParseFailedException
	 */
	public static List<Event> processDocument(Document d,
			Annotator ptcEn, Annotator ptcMcc, Annotator ptcEv,
			Annotator ptcNegSpec) throws UnrecognisedEventTypeException, ParseFailedException {


		String text = d.toString();
		String id = d.getID();
		//			System.out.println(id);

		List<Map<String,String>> mappedEvents = ptcEv.processDoc(d);		// "id","trigger_start", "trigger_end", "trigger_text", "type", "participants"

		if (mappedEvents.size() == 0)
			return new ArrayList<Event>();

		List<Map<String,String>> mappedParses = ptcMcc.processDoc(d);	// "id","mcclosky_data" // id starts from 0 and is per sentence
		List<Map<String,String>> mappedEntities = ptcEn.processDoc(d);	// "id","entity_id","entity_start","entity_end","entity_term","entity_group"
		List<Map<String,String>> mappedNegSpecs = ptcNegSpec != null ? ptcNegSpec.processDoc(d) : null;	// "event_id", "negated", "speculated"
		//SentenceSplitter sentences = new SentenceSplitter(text);	// for (Pair<Integer> p : sentences)

		Collections.sort(mappedEvents, EventCombiner.getEventComparator());
		Misc.sort(mappedParses);
		Misc.sort(mappedEntities);

		//Making a forest of parses
		List<Node> roots = new ArrayList<Node>(mappedParses.size());

		List<Event> events = new ArrayList<Event>(mappedEvents.size());

		//the argument of the Node constructor is the tag
		Node forest = new Node("FOREST");

		for (Map<String, String> parse : mappedParses){
			String aParse = parse.get("mcclosky_data");
			Node root = Node.makeTree(aParse);
			roots.add(root);
			forest.addChild(root);

		}

		//		System.out.println(forest.toString());
		//		System.exit(0);

		for (Map<String, String> mappedEvent : mappedEvents){
			Event e;
			String eventId = mappedEvent.get("id");
			//System.out.println(mappedNegSpecs);
			//System.out.println("Looking for " + id + "\t" + eventId);

			Map<String,String> mappedNegSpec = null;

			if (mappedNegSpecs != null){ 
				for (Map<String,String> m : mappedNegSpecs){
					if(!m.get("doc_id").equals(id)){
						throw new IllegalStateException("Exceptional situation occured!");
					}
					if (m.get("event_id").equals(eventId)){
						mappedNegSpec = m;
						break;
					}
				}
			}

			String participantsNames = mappedEvent.get("participants");
			//System.out.println("participantsNames" + participantsNames);

			if (SimpleEvent.EVENT_TYPES.contains(mappedEvent.get("type"))){
				Map<String,String> mappedTheme = Misc.getByID(mappedEntities, participantsNames.substring(2)); //get rid of the beginning "|T"
				e = new SimpleEvent(d.getID(), mappedEvent, mappedNegSpec, mappedTheme, forest, text);
				events.add(e);
			}else if (BindingEvent.EVENT_TYPES.contains(mappedEvent.get("type"))){
				List<Map<String, String>> mappedThemes = new ArrayList<Map<String, String>>();
				for (String t : participantsNames.substring(1).split(",")){		//get rid of the beginning "|"
					Map<String,String> mappedTheme = Misc.getByID(mappedEntities, t.substring(1)); //get rid of the beginning "T" 
					mappedThemes.add(mappedTheme);
				}
				e = new BindingEvent(d.getID(), mappedEvent, mappedNegSpec, mappedThemes, forest, text);
				events.add(e);
			}else if (RegulationEvent.EVENT_TYPES.contains(mappedEvent.get("type"))){
				String[] particNames = new String[2];
				particNames = participantsNames.split("\\|");
				String themeName = particNames[1];
				String causeName = particNames[0];
				//System.out.println(themeName + "*\t*" + causeName);
				Map<String,String> mappedTheme = null, mappedCause = null;
				String themeType = "", causeType = "";
				if (themeName.startsWith("T")){
					mappedTheme = Misc.getByID(mappedEntities, themeName.substring(1));
					themeType = "T";
				}else if (themeName.startsWith("E")){
					mappedTheme = Misc.getByID(mappedEvents, themeName);
					themeType = "E";
				}

				if (causeName.equals("")){
					causeType = "";
				}else if (causeName.startsWith("T")){
					mappedCause = Misc.getByID(mappedEntities, causeName.substring(1));
					causeType = "T";
				}else if (causeName.startsWith("E")){
					mappedCause = Misc.getByID(mappedEvents, causeName);
					causeType = "E";
				}

				//System.out.println(mappedTheme);
				//System.out.println(mappedCause);

				e = new RegulationEvent(d.getID(), mappedEvent, mappedNegSpec, themeType, mappedTheme, causeType, mappedCause, forest, text);
				events.add(e);
			}else {
				throw new UnrecognisedEventTypeException("Event type " + mappedEvent.get("type") + " isn't a recognised type");
			}
		}

		return events;
	}

	public static void main(String[] args) throws UnrecognisedEventTypeException, MalformedURLException, ParseException{
		ArgParser ap = ArgParser.getParser(args);	

		NegmoleBuild.sem = ap.containsKey("sem");
		
		Logger logger = Loggers.getDefaultLogger(ap);
		DocumentIterator documents = DocumentParser.getDocuments(ap, logger);

		logger.info("%t: Loading gold data...\n");
		List<Event> goldEvents = getGoldData(documents);
		logger.info("%t: Done.\n");

		TextPipe.verbosity = TextPipe.getVerbosity(ap);

		List<NegmoleFeaturedEvent> negFeaturedEvents = NegmoleFeaturedEvent.getNegmoleFeaturedEventList(goldEvents, NegOrSpec.N);
		List<NegmoleFeaturedEvent> specFeaturedEvents = NegmoleFeaturedEvent.getNegmoleFeaturedEventList(goldEvents, NegOrSpec.S);

		if (ap.containsKey("perfTrain")){
			System.out.println("Starting perf train...");
			SvmPerfWrapper spwtn = new SvmPerfWrapper(negFeaturedEvents, NegOrSpec.N, ap.getFile("perfDir", new File("svm_perf/")), ap.getFile("perfModelDir", new File("/tmp/farzin-models/")));
			spwtn.train();
			System.out.println("Neg training done.");

			SvmPerfWrapper spwts = new SvmPerfWrapper(specFeaturedEvents, NegOrSpec.S, ap.getFile("perfDir", new File("svm_perf/")), ap.getFile("perfModelDir", new File("/tmp/farzin-models/")));
			spwts.train();
			System.out.println("Spec training done.");
			System.out.println("Perf training done.");
		}

		if (ap.containsKey("test2")){
			test2(negFeaturedEvents);
		}

		if (ap.containsKey("perfTest")){
			File logDir = ap.getFile("logDir");

			SvmPerfWrapper spwtn = new SvmPerfWrapper(negFeaturedEvents, NegOrSpec.N, ap.getFile("perfDir", new File("svm_perf/")), ap.getFile("perfModelDir", new File("/tmp/farzin-models/")));
			spwtn.test(logDir);
			System.out.println("Neg test done.");

			SvmPerfWrapper spwts = new SvmPerfWrapper(specFeaturedEvents, NegOrSpec.S, ap.getFile("perfDir", new File("svm_perf/")), ap.getFile("perfModelDir", new File("/tmp/farzin-models/")));
			spwts.test(logDir);
			System.out.println("Spec test done.");

			System.out.println("Perf test done.");
		}

		if (ap.containsKey("speculation")){
			for (NegmoleFeaturedEvent e : specFeaturedEvents){
				if (e.isTargetSpeculated()){

					String sentence = "";
					for (Node n : e.getEvent().getTrigger().getRoot().getLeaves()){
						sentence += n.getData().get("text");
						sentence += " ";
					}
					System.out.println(sentence);
				}
			}
		}
	}

	private static void test2(List<NegmoleFeaturedEvent> negFeaturedEvents) {

		Map<Integer,Integer> keyToMod = new HashMap<Integer,Integer>();

		System.out.println("Features.categoryDims.get(Classes.I.hashCode() + NegOrSpec.N.hashCode())");
		Set<Integer> allKeys = Features.categoryDims.get(Classes.III.hashCode() + NegOrSpec.N.hashCode());
		int counter = 1;
		for (int k : allKeys){
			//System.out.println(k + "\t" + counter);
			keyToMod.put(k, counter++);
		}

		for (NegmoleFeaturedEvent e : negFeaturedEvents){
			System.out.println(e.getEvent().getId() + "\t" + e.isTargetNegated() + "\t" + e.isTargetSpeculated());

			for (Integer k : e.getFeatureDims2Values().keySet()){
				if (k == null)
					System.out.println("*** NULL FEATURE KEY ***");
				else {
					if (!Features.allPossibleValuesReverse.containsKey(k))
						throw new IllegalStateException(""+k);
					if (Features.allPossibleValuesReverse.get(k) == null)
						throw new IllegalStateException(""+k);
					if (!e.getFeatureDims2Values().containsKey(k))
						throw new IllegalStateException(""+k);
					if (e.getFeatureDims2Values().get(k) == null)
						throw new IllegalStateException(""+k);

					if (e.getFeatureDims2Values().get(k) != 0)
						System.out.println("\t" + k + "\t" + keyToMod.get(k) + "\t" + Features.allPossibleValuesReverse.get(k) + "\t" + e.getFeatureDims2Values().get(k));
				}
			}
		}

	}
}
