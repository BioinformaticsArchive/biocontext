package uk.ac.man.biocontext.wrappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import java.util.regex.Matcher;

import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;

import martin.common.ArgParser;
import martin.common.Pair;

import uk.ac.man.biocontext.tools.anatomy.Anatomy;
import uk.ac.man.biocontext.wrappers.events.EventCombiner;
import uk.ac.man.biocontext.wrappers.genener.*;
import uk.ac.man.entitytagger.generate.GenerateMatchers;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;

import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class ContrastWrapper extends Annotator {

	private Annotator ptcEvents;
	private Annotator ptcEntities;
	private Annotator ptcAnatomy;

	//private String[] contrastPatterns = new String[]{".*$1( strain)?,? but not $2.*", ".*unlike $1, $2.*"};
	private String[] contrastPatterns = new String[]{
			".*($1,? but not ([a-z]+ )?$2).*", 
			".*([Uu]nlike $1, $2).*",
	};
	//	private String[] contrastPatterns = new String[]{".*$1,?( [a-z]+) but not ([a-z]+ )?$2.*", ".*$1( [a-z]+), but not ([a-z]+ )?$2.*"};
	//	private String[] contrastPatterns = new String[]{".*$1,? but not $2.*", ".*unlike $1, $2.*"};
	//private String[] contrastPatterns = new String[]{".*$1.*but not $2.*", ".*unlike $1, $2.*"};

	public static String[] outputFields;

	static {

		String[] newFields = new String[]{"inferred_contrast","contrast_cue"};

		outputFields = new String[EventCombiner.outputFields.length + newFields.length];

		int c = 0;

		for (String f : EventCombiner.outputFields){
			outputFields[c++] = f;
		}
		for (String f : newFields){
			outputFields[c++] = f;
		}
	}

	@Override
	public void init(java.util.Map<String,String> data) {
		this.ptcEvents = new PrecomputedAnnotator(Anatomy.outputFields, "farzin", "data_anatomy", ArgParser.getParser());
		this.ptcEntities = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser());
		this.ptcAnatomy = new PrecomputedAnnotator(LinnaeusWrapper.outputFields, "farzin", "data_l_anatomy", ArgParser.getParser());
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {

		List<Map<String, String>> entities = ptcEntities.process(data);

		String text = data.get("doc_text");
		Map<String, String> contrastingEntities = getContrastingEntities(text, entities, data);

		List<Map<String, String>> anatomies = ptcAnatomy.process(data);
		Map<String, String> contrastingAnatomies = getContrastingEntities(text, anatomies, data);

		if (contrastingEntities.size() == 0 && contrastingAnatomies.size() == 0){
			return new ArrayList<Map<String, String>>();
		}


		List<Map<String, String>> events = ptcEvents.process(data);

		//createEvents(events, contrastingEntities, "E");
		createEvents(events, contrastingAnatomies, "A");


		List<Map<String, String>> res = new ArrayList<Map<String,String>>();


		return res;
	}



	private void createEvents(List<Map<String, String>> events, Map<String, String> contrastingEntities, String type) {
		for (String entityWithContrast : contrastingEntities.keySet()){
			for (Map<String, String> event : events){
				//debug
				if (type.equals("A") && event.get("anatomy_id")!= null && event.get("anatomy_id").equals(contrastingEntities.get(entityWithContrast))){
					TextPipe.printData(event);
				}
				
				
				if ((type.equals("E") && EventCombiner.hasParticipant(event, "T" + entityWithContrast))
				  || type.equals("A") && event.get("anatomy_id")!= null && event.get("anatomy_id").equals(entityWithContrast)){

					TextPipe.printData(event);

					boolean found = false;
					for (Map<String, String> event2: events){
						if ((type.equals("E") && EventCombiner.hasParticipant(event2, "T" + contrastingEntities.get(entityWithContrast)))
						  || type.equals("A") && event2.get("anatomy_id")!= null && event2.get("anatomy_id").equals(contrastingEntities.get(entityWithContrast))){
							TextPipe.printData(event2);
							found = true;
						}
					}
					if (!found){
						System.out.println("Possible contrast inference!");
						TextPipe.printData(event);
						System.out.println("*");
					}
				}
			}
		}		
	}

	private Map<String, String> getContrastingEntities(String text,	List<Map<String, String>> entities, Map<String, String> data) {
		Map<String, String> res = new HashMap<String, String>();

		int c = 0;
		text = text.replace("\n"," ");
		//System.out.print(text);

		try{

			for (String pattern : contrastPatterns){
				//System.out.println(pattern.replace("$1", "").replace("$2", ""));
				if (text.matches(pattern.replace("$1", "").replace("$2", ""))){
					//System.out.print(pattern.replace("$1", "").replace("$2", ""));
					//System.out.print("X");
					for (Map<String, String> e1 : entities){
						//System.out.print("c");
						//System.out.println("YYYYYYYYYYYYYYYYYYYY");
						String pattern2 = pattern.replace("$1", GenerateMatchers.escapeRegexp(e1.get("entity_term")));
						//System.out.println(pattern2.replace("$2", ""));
						if (text.matches(pattern2.replace("$2", ""))){
							//System.out.println(pattern2.replace("$2", ""));
							for (Map<String, String> e2 : entities){
								String pattern3 = pattern2.replace("$2", GenerateMatchers.escapeRegexp(e2.get("entity_term")));

								Matcher matcher = Pattern.compile(pattern3).matcher(text);
								while (matcher.find()){
									int matchStart = matcher.start(1);
									int matchEnd = matcher.end(1);

									int start1 = Integer.parseInt(e1.get("entity_start"));
									int end1 = Integer.parseInt(e1.get("entity_end"));
									int start2 = Integer.parseInt(e2.get("entity_start"));
									int end2 = Integer.parseInt(e2.get("entity_end"));

									if (matchStart <= Math.min(start1,start2)
											&&
											matchEnd >= Math.max(end1,end2)){
										System.out.println("\n" + data.get("doc_id"));
										System.out.println(text.substring(Math.max(0, matchStart-20),Math.min(text.length(), matchEnd+20)));

										res.put(e1.get("id"), e2.get("id"));
										c++;
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
		return res;
	}

	private void doCue(List<Map<String, String>> res, List<Map<String, String>> events, Pair<Integer> sentOffsets, Map<String, String> data, String cue) {
		String sentence = data.get("doc_text").substring(sentOffsets.getX(), sentOffsets.getY());


		List<Map<String,String>> localEvents = new ArrayList<Map<String,String>>();
		for (Map<String,String> e: events){

			int triggerStart = Integer.parseInt(e.get("trigger_start"));
			int triggerEnd = Integer.parseInt(e.get("trigger_end"));

			if (triggerStart >= sentOffsets.getX() && triggerEnd <= sentOffsets.getY()){
				localEvents.add(e);
			}
		}

		if (localEvents.size() > 1){
			int cueOffset = sentence.indexOf(cue) + sentOffsets.getX();

			for (Map<String,String> pivot: localEvents){
				//				if (!sentence.contains(pivot.get("trigger_text"))){
				//					throw new IllegalStateException("local event not in sentence");
				//				}

				Map<String,String> item = new HashMap<String, String>();
				int pivotTriggerOffset = Integer.parseInt(pivot.get("trigger_start"));

				for (Map<String,String> other: localEvents){
					//					if (!sentence.contains(other.get("trigger_text"))){
					//						throw new IllegalStateException("local event not in sentence");
					//					}	
					int otherTriggerOffset = Integer.parseInt(other.get("trigger_start"));

					if ((pivotTriggerOffset - cueOffset) * (otherTriggerOffset - cueOffset) < 0){
						if (item.containsKey("id")){
							String oldValue = item.get("contrasts_with");
							item.put("contrasts_with", oldValue + "|" + other.get("id"));
						} else {
							item.put("id", pivot.get("id"));
							item.put("contrasts_with", other.get("id"));
						}
						item.put("contrast_cue", cue);
						item.put("sentence", sentence);
					}
				}

				if (!item.isEmpty()){
					res.add(item);
					//TextPipe.printData(item);
				}
			}
		}

	}

	@Override
	public String[] getOutputFields() {
		return outputFields;
	}
}


