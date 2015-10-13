package uk.ac.man.farzin.wrappers.events;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import uk.ac.man.pubtools.PubTools;

public class EventCombinerTest {


	List<Map<String, String>> events;
	List<Map<String, String>> genes;
	
	@Before
	public void init(){


		events = new ArrayList<Map<String, String>>();
		genes = new ArrayList<Map<String, String>>();
		
		Map<String,String> e = new HashMap<String,String>();
		e.put("id", "E1");
		e.put("type", "Localization");
		e.put("participants", "|T0");
		e.put("trigger_start", "0");
		e.put("trigger_end", "10");
		e.put("level", "0");

		events.add(e);

		Map<String,String> g = new HashMap<String,String>();
		g.put("id","0");
		g.put("entity_group","0");
		g.put("entity_start", "100");
		g.put("entity_end", "101");
		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","1");
		g.put("entity_group","0");
		g.put("entity_start", "111");
		g.put("entity_end", "112");
		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","2");
		g.put("entity_start", "122");
		g.put("entity_end", "123");
		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","3");
		g.put("entity_group","1");
		g.put("entity_start", "133");
		g.put("entity_end", "134");
		genes.add(g);

		Map<String,String> e2 = new HashMap<String,String>();
		e2.put("id", "E2");
		e2.put("type", "Binding");
		e2.put("participants", "|T0,T2");
		e2.put("trigger_start", "0");
		e2.put("trigger_end", "10");
		e2.put("level", "0");
		events.add(e2);
	}

	@Test
	public void duplicateEventsTest(){
		
		new EventCombiner().duplicateEvents(events, genes);

		
		
		assertEquals(3, events.size());
		
		boolean contains0 = false;
		boolean contains1 = false;
		
		for (Map<String,String> event : events){
			assertFalse(event.get("participants").equals("|T2"));
			assertFalse(event.get("participants").equals("|T3"));
			if (event.get("participants").equals("|T0")){
				assertFalse(event.containsKey("inferred"));
				contains0 = true;
			} if (event.get("participants").equals("|T1")){
				assertTrue(event.get("inferred").equals("E1"));
				contains1 = true;
			}
		}
		
		assertTrue(contains0);
		assertTrue(contains1);

		testNumbering();
	}
	
	public void testNumbering(){
		for (int i = 0; i < events.size(); i++){
			assertEquals(events.get(i).get("id"), "E" + (i + 1));			
		}
	}
	
	@Test
	public void duplicateRegEventsTest(){
		Map<String,String> e2 = new HashMap<String,String>();
		e2.put("id", "E3");
		e2.put("type", "Regulation");
		e2.put("participants", "T2|T0");
		e2.put("trigger_start", "0");
		e2.put("trigger_end", "10");
		e2.put("level", "0");
		events.add(e2);
		
		
		new EventCombiner().duplicateEvents(events, genes);
		
		assertEquals(5, events.size());

		testNumbering();
	}
	
	@Test
	public void duplicateRegEventTest2(){

		genes = new ArrayList<Map<String, String>>();
		Map<String,String> g = new HashMap<String,String>();
		g.put("id","0");
		//g.put("entity_group","0");
		g.put("entity_start", "100");
		g.put("entity_end", "101");
		genes.add(g);

//		g = new HashMap<String,String>();
//		g.put("id","1");
//		g.put("entity_start", "111");
//		g.put("entity_end", "112");
//		g.put("entity_group","0");
//		
//		genes.add(g);
//		
//		g = new HashMap<String,String>();
//		g.put("id","2");
//		g.put("entity_group","0");
//		g.put("entity_start", "122");
//		g.put("entity_end", "123");
//		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","10");
		g.put("entity_group","1");
		g.put("entity_start", "133");
		g.put("entity_end", "134");
		genes.add(g);
		
		g = new HashMap<String,String>();
		g.put("id","11");
		g.put("entity_group","1");
		g.put("entity_start", "144");
		g.put("entity_end", "145" );
		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","12");
		g.put("entity_group","1");
		g.put("entity_start", "154");
		g.put("entity_end", "155" );
		genes.add(g);

		events = new ArrayList<Map<String, String>>();
		
		Map<String,String> e2 = new HashMap<String,String>();
		e2.put("id", "E1");
		e2.put("type", "Regulation");
		e2.put("participants", "T0|T10");
		e2.put("trigger_start", "0");
		e2.put("trigger_end", "10");
		e2.put("level", "0");
		events.add(e2);
		
		
		e2 = new HashMap<String,String>();
		e2.put("id", "E2");
		e2.put("type", "Regulation");
		e2.put("participants", "T0|T11");
		e2.put("trigger_start", "0");
		e2.put("trigger_end", "10");
		e2.put("level", "0");
		events.add(e2);
		
//		e2 = new HashMap<String,String>();
//		e2.put("id", "E3");
//		e2.put("type", "Regulation");
//		e2.put("participants", "T1|T10");
//		e2.put("trigger_start", "0");
//		e2.put("trigger_end", "10");
//		e2.put("level", "0");
//		events.add(e2);
//		
//		e2 = new HashMap<String,String>();
//		e2.put("id", "E4");
//		e2.put("type", "Regulation");
//		e2.put("participants", "T0|T11");
//		e2.put("trigger_start", "0");
//		e2.put("trigger_end", "10");
//		e2.put("level", "0");
//		events.add(e2);
		
		
		int size = events.size();
		new EventCombiner().duplicateEvents(events, genes);
		
		
//		while (size != events.size()){
//			size = events.size();
//
//			PubTools.printData(events);
//
//			
//			new EventCombiner().duplicateEvents(events, genes, null);			
//
//			System.out.println("*************************************");
//			PubTools.printData(events);
//
//			
//			System.out.println("SIZE IS NOW " + size);
//			System.out.println("Events size IS NOW " + events.size());
//
//		}
		
		
		assertEquals(3, events.size());
		
	}
	
	@Test
	public void duplicateRegEventTest3(){

		genes = new ArrayList<Map<String, String>>();
		Map<String,String> g = new HashMap<String,String>();
		g.put("id","0");
		g.put("entity_group","0");
		g.put("entity_start", "100");
		g.put("entity_end", "101");
		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","1");
		g.put("entity_start", "111");
		g.put("entity_end", "112");
		g.put("entity_group","0");
		
		genes.add(g);
		
//		g = new HashMap<String,String>();
//		g.put("id","2");
//		g.put("entity_group","0");
//		g.put("entity_start", "122");
//		g.put("entity_end", "123");
//		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","10");
		g.put("entity_group","1");
		g.put("entity_start", "133");
		g.put("entity_end", "134");
		genes.add(g);
		
		g = new HashMap<String,String>();
		g.put("id","11");
		g.put("entity_group","1");
		g.put("entity_start", "144");
		g.put("entity_end", "145" );
		genes.add(g);

		g = new HashMap<String,String>();
		g.put("id","12");
		g.put("entity_group","1");
		g.put("entity_start", "154");
		g.put("entity_end", "155" );
		genes.add(g);

		events = new ArrayList<Map<String, String>>();
		
		Map<String,String> e2 = new HashMap<String,String>();
		e2.put("id", "E1");
		e2.put("type", "Regulation");
		e2.put("participants", "T0|T10");
		e2.put("trigger_start", "0");
		e2.put("trigger_end", "10");
		e2.put("level", "0");
		events.add(e2);
		
		
//		e2 = new HashMap<String,String>();
//		e2.put("id", "E2");
//		e2.put("type", "Regulation");
//		e2.put("participants", "T0|T11");
//		e2.put("trigger_start", "0");
//		e2.put("trigger_end", "10");
//		e2.put("level", "0");
//		events.add(e2);
//		
//		e2 = new HashMap<String,String>();
//		e2.put("id", "E3");
//		e2.put("type", "Regulation");
//		e2.put("participants", "T1|T10");
//		e2.put("trigger_start", "0");
//		e2.put("trigger_end", "10");
//		e2.put("level", "0");
//		events.add(e2);
//		
//		e2 = new HashMap<String,String>();
//		e2.put("id", "E4");
//		e2.put("type", "Regulation");
//		e2.put("participants", "T0|T11");
//		e2.put("trigger_start", "0");
//		e2.put("trigger_end", "10");
//		e2.put("level", "0");
//		events.add(e2);
		
		
		new EventCombiner().duplicateEvents(events, genes);
		PubTools.printData(events);
		
		assertEquals(6, events.size());
		
	}
	
	
	
	//Test for when both participants are neighbours
}
