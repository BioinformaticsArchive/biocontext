package uk.ac.man.biocontext.util.annotators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipeClient;

public class TriggerParticipantCollision extends Annotator {
	private Annotator ptcGenes;
	private Annotator ptcEvents;

	@Override
	public void init(Map<String, String> data) {
		this.ptcGenes = TextPipeClient.get(data, "genes", "db001", 57004, "farzin", "data_genes");
		this.ptcEvents = TextPipeClient.get(data, "events", "db001", 57023, "farzin", "data_events");
	}
	
	@Override
	public String[] getOutputFields() {
		return new String[]{"trigger_text", "participant"};
	}
	
	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		List<Map<String, String>> events = ptcEvents.process(data);
		List<Map<String, String>> genes = ptcGenes.process(data);
		

		List<Map<String,String>> res = new ArrayList<Map<String,String>>();

		for (Map<String,String> event : events){
			boolean overlaps = false;

			
			int eventStart = Integer.parseInt(event.get("trigger_start"));
			int eventEnd = Integer.parseInt(event.get("trigger_end"));
			String theme = null;
			
			for(String participant : event.get("participants").split("\\|")[1].split(",")){
				int participantStart=0, participantEnd=0;
				
				if (participant.startsWith("T")){
					Map<String,String> pMap = Misc.getByID(genes, participant.substring(1));
					participantStart = Integer.parseInt(pMap.get("entity_start"));
					participantEnd = Integer.parseInt(pMap.get("entity_end"));
				}
				if (participant.startsWith("E")){
					Map<String,String> pMap = Misc.getByID(events, participant);
					participantStart = Integer.parseInt(pMap.get("trigger_start"));
					participantEnd = Integer.parseInt(pMap.get("trigger_end"));
				}
				
				if ((eventStart >= participantStart && eventStart < participantEnd) || (participantStart >= eventStart && participantStart < eventEnd)){
					System.out.println(eventStart + ", " + eventEnd + "\n" + participantStart + ", " + participantEnd + "\n");
					theme = data.get("doc_text").substring(participantStart, participantEnd);
					overlaps=true;
				}
			}
			
			if (overlaps){
				Map<String,String> map = new HashMap<String,String>();
				map.put("trigger_text", event.get("trigger_text"));
				
				map.put("participant", theme);
				res.add(map);
			}
		}
		TextPipe.printData(genes);
		TextPipe.printData(events);
		System.out.println(data.get("doc_text"));
		return res;
		
	}
}
