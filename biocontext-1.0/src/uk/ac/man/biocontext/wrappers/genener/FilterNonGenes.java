package uk.ac.man.biocontext.wrappers.genener;

import java.util.HashSet;
import java.util.Set;

import gnat.filter.Filter;
import gnat.representation.Context;
import gnat.representation.GeneRepository;
import gnat.representation.RecognizedEntity;
import gnat.representation.Text;
import gnat.representation.TextRepository;
import gnat.representation.TextAnnotation.Type;

public class FilterNonGenes implements Filter {

	@Override
	public void filter(Context context, TextRepository arg1, GeneRepository arg2) {
		for (Text text : context.getTexts()){
			Set<RecognizedEntity> ents = context.getRecognizedEntitiesInText(text);

			Set<String> speciesTerms = new HashSet<String>();
			
			for (RecognizedEntity e : ents){
				Type t = e.getAnnotation().getType();
				
				if (t != Type.GENE && t != Type.PROTEIN){
					speciesTerms.add(e.getName());
				}
			}

			for (RecognizedEntity e : ents){
				if (speciesTerms.contains(e.getName())){
					context.removeRecognizedEntity(e);		
				}
			}
		}
	}
}
