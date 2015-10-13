package uk.ac.man.biocontext.wrappers.events;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;
import martin.common.Misc;

import uk.ac.man.biocontext.evaluate.Evaluate;
import uk.ac.man.biocontext.evaluate.Evaluate.Approx;
import uk.ac.man.biocontext.wrappers.genener.GeneCombiner;
import uk.ac.man.biocontext.wrappers.genener.GeneNERWrapper;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class TurkuMerger extends Annotator {
	private PrecomputedAnnotator manchesterTurkuAnnotator;
	private PrecomputedAnnotator turkuTurkuAnnotator;

	private PrecomputedAnnotator manchesterGeneAnnotator;
	private PrecomputedAnnotator turkuGeneAnnotator;
	private Set<String> medline_2009_ids;

	@Override
	public String[] getOutputFields() {
		return TurkuWrapper.outputFields;
	}

	@Override
	public void init(Map<String, String> data) {
		this.manchesterTurkuAnnotator = new PrecomputedAnnotator(TurkuWrapper.outputFields, "farzin", "data_e_turku_manchester", ArgParser.getParser());
		this.turkuTurkuAnnotator = new PrecomputedAnnotator(TurkuWrapper.outputFields, "farzin", "data_turku_events", ArgParser.getParser());

		this.manchesterGeneAnnotator = new PrecomputedAnnotator(GeneCombiner.outputFields, "farzin", "data_genes", ArgParser.getParser());
		this.turkuGeneAnnotator = new PrecomputedAnnotator(Evaluate.goldGeneColumns, "farzin", "data_turku_entities", ArgParser.getParser());
		
		this.medline_2009_ids = Misc.loadStringSetFromFile(new File(data.get("2009_ids")));
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		List<Map<String,String>> turkuGenes = turkuGeneAnnotator.process(data);

		if (!medline_2009_ids.contains(data.get("doc_id"))){
			return manchesterTurkuAnnotator.process(data);
		}

		List<Map<String,String>> turkuEvents = turkuTurkuAnnotator.process(data);

		if (turkuEvents.size() == 0)
			return turkuEvents;

		List<Map<String,String>> manchesterGenes = manchesterGeneAnnotator.process(data);

		renameGeneParticipants(turkuEvents, turkuGenes, manchesterGenes);

		return turkuEvents;		
	}

	private void renameGeneParticipants(List<Map<String, String>> turkuEvents,
			List<Map<String, String>> turkuGenes,
			List<Map<String, String>> manchesterGenes) {

		boolean changed = false;

		for (int i = 0; i < turkuEvents.size(); i++){
			Map<String,String> e = turkuEvents.get(i);
			boolean remove = false;

			String[] causes = e.get("participants").split("\\|")[0].split(",");
			String[] themes = e.get("participants").split("\\|")[1].split(",");

			for (int j = 0; j < causes.length; j++){
				if (causes[j].startsWith("T")){
					causes[j] = findEquivalentGene(turkuGenes, manchesterGenes, causes[j]);
					if (causes[j] == null)
						remove=true;
				}
			}
			for (int j = 0; j < themes.length; j++){
				if (themes[j].startsWith("T")){
					themes[j] = findEquivalentGene(turkuGenes, manchesterGenes, themes[j]);
					if (themes[j] == null)
						remove=true;
				}
			}

			if (remove){
				turkuEvents.remove(i--);
				changed = true;
			} else {
				e.put("participants", Misc.implode(causes, ",") + "|" + Misc.implode(themes, ","));
			}
		}

		while (changed){
			changed = false;

			for (int i = 0; i < turkuEvents.size(); i++){
				Map<String,String> e = turkuEvents.get(i);
				boolean remove = false;

				String[] causes = e.get("participants").split("\\|")[0].split(",");
				String[] themes = e.get("participants").split("\\|")[1].split(",");

				for (int j = 0; j < causes.length; j++){
					if (causes[j].startsWith("E")){
						if (uk.ac.man.biocontext.util.Misc.getByID(turkuEvents,causes[j]) == null){
							remove = true;
						}
					}
				}
				for (int j = 0; j < themes.length; j++){
					if (themes[j].startsWith("E")){
						if (uk.ac.man.biocontext.util.Misc.getByID(turkuEvents,themes[j]) == null){
							remove = true;							
						}
					}
				}

				if (remove){
					turkuEvents.remove(i--);
					changed = true;
				}
			}
		}
	}

	private String findEquivalentGene(List<Map<String, String>> turkuGenes,
			List<Map<String, String>> manchesterGenes, String turkuid) {

		turkuid = turkuid.substring(1);

		Map<String,String> turkuGene = uk.ac.man.biocontext.util.Misc.getByID(turkuGenes, turkuid);

		if (turkuGene == null)
			throw new IllegalStateException("Could not find gene with id " + turkuid);

		for (Map<String,String> mg : manchesterGenes){
			if (GeneNERWrapper.equalsIgnoreID(turkuGene, mg, Approx.APPROX)){
				return "T" + mg.get("id");
			}
		}

		return null;		
	}
}